#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
cd "${REPO_ROOT}"

baseline_schema="deploy/database/nacos/010_schema.sql"
bootstrap_script="deploy/database/nacos/001_bootstrap.sh"
compatibility_migration="deploy/database/nacos/020_nacos31_compatibility.sql"

if ! grep -Fq 'nacos/nacos-server:v3.1.2-slim' "${baseline_schema}"; then
  echo 'Nacos schema baseline must match nacos/nacos-server:v3.1.2-slim' >&2
  exit 1
fi
if [ ! -f "${compatibility_migration}" ]; then
  echo 'Nacos 3.1 compatibility migration is missing' >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
fake_bin="${tmp_dir}/bin"
migration_input="${tmp_dir}/migration-input.sql"
invocation_count_file="${tmp_dir}/invocation-count"
bootstrap_dir="${tmp_dir}/bootstrap"
mkdir -p "${fake_bin}" "${bootstrap_dir}"
trap 'rm -rf "${tmp_dir}"' EXIT
printf '%s\n' '-- nacos31 compatibility migration entrypoint contract' >"${bootstrap_dir}/020_nacos31_compatibility.sql"
printf '0\n' >"${invocation_count_file}"

cat >"${fake_bin}/mysql" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

if [[ " $* " == *' -N -B '* ]]; then
  printf '1\n'
else
  invocation_count="$(cat "${FAKE_NACOS_INVOCATION_COUNT}")"
  printf '%s\n' "$((invocation_count + 1))" >"${FAKE_NACOS_INVOCATION_COUNT}"
  cat >>"${FAKE_NACOS_MIGRATION_INPUT}"
fi
EOF
chmod +x "${fake_bin}/mysql"

PATH="${fake_bin}:${PATH}" \
  FAKE_NACOS_MIGRATION_INPUT="${migration_input}" \
  FAKE_NACOS_INVOCATION_COUNT="${invocation_count_file}" \
  MYSQL_ROOT_PASSWORD=contract-root-password \
  BOOTSTRAP_DIR="${bootstrap_dir}" \
  "${bootstrap_script}" >/dev/null
if ! grep -Fq 'nacos31 compatibility migration entrypoint contract' "${migration_input}"; then
  echo 'Nacos database bootstrap must run the 3.1 compatibility migration for an existing schema' >&2
  exit 1
fi

PATH="${fake_bin}:${PATH}" \
  FAKE_NACOS_MIGRATION_INPUT="${migration_input}" \
  FAKE_NACOS_INVOCATION_COUNT="${invocation_count_file}" \
  MYSQL_ROOT_PASSWORD=contract-root-password \
  BOOTSTRAP_DIR="${bootstrap_dir}" \
  "${bootstrap_script}" >/dev/null
if [ "$(cat "${invocation_count_file}")" -lt 2 ]; then
  echo 'Nacos database bootstrap must rerun the compatibility migration on every start' >&2
  exit 1
fi
grep -Fq 'CREATE TABLE IF NOT EXISTS' "${compatibility_migration}"
grep -Fq 'information_schema.columns' "${compatibility_migration}"

for required_structure in config_info_gray publish_type gray_name ext_info; do
  if ! rg -Fq "${required_structure}" "${baseline_schema}" "${compatibility_migration}"; then
    echo "Nacos 3.1.2 schema compatibility must include ${required_structure}" >&2
    exit 1
  fi
done

schema_container="nacos-schema-contract-$$"
cleanup_schema_container() {
  docker rm -f "${schema_container}" >/dev/null 2>&1 || true
}
trap 'cleanup_schema_container; rm -rf "${tmp_dir}"' EXIT

docker run -d --rm --name "${schema_container}" \
  -e MYSQL_ROOT_PASSWORD=contract-root-password \
  -e MYSQL_DATABASE=nacos \
  mysql:8.0 >/dev/null
for attempt in $(seq 1 60); do
  if docker exec -e MYSQL_PWD=contract-root-password "${schema_container}" mysqladmin ping \
      -h127.0.0.1 -uroot --silent >/dev/null 2>&1; then
    break
  fi
  if [ "${attempt}" -eq 60 ]; then
    echo 'contract MySQL did not become ready' >&2
    exit 1
  fi
  sleep 1
done
docker exec -e MYSQL_PWD=contract-root-password -i "${schema_container}" mysql --default-character-set=utf8mb4 \
  -uroot nacos <"${baseline_schema}"
docker exec -e MYSQL_PWD=contract-root-password -i "${schema_container}" mysql --default-character-set=utf8mb4 \
  -uroot nacos <"${compatibility_migration}" >/dev/null
docker exec -e MYSQL_PWD=contract-root-password -i "${schema_container}" mysql --default-character-set=utf8mb4 \
  -uroot nacos <"${compatibility_migration}" >/dev/null
schema_check="$(docker exec -e MYSQL_PWD=contract-root-password "${schema_container}" mysql -N -B \
  -uroot nacos -e \
  "select count(*) from information_schema.tables where table_schema = 'nacos' and table_name = 'config_info_gray';")"
test "${schema_check}" = '1'
for required_column in publish_type gray_name ext_info; do
  schema_check="$(docker exec -e MYSQL_PWD=contract-root-password "${schema_container}" mysql -N -B \
    -uroot nacos -e \
    "select count(*) from information_schema.columns where table_schema = 'nacos' and table_name = 'his_config_info' and column_name = '${required_column}';")"
  test "${schema_check}" = '1'
done
