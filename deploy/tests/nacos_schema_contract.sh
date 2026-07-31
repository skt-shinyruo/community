#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
cd "${REPO_ROOT}"

baseline_schema="deploy/mysql/nacos/010_schema.sql"
bootstrap_script="deploy/mysql/nacos/001_bootstrap.sh"
compatibility_migration="deploy/mysql/nacos/020_nacos31_compatibility.sql"

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
