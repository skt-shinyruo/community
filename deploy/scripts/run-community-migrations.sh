#!/usr/bin/env bash
set -euo pipefail

# The image mounts the reviewed migration set at this fixed location. Keeping
# the location non-configurable prevents an operator from injecting ad-hoc SQL.
readonly MIGRATION_DIRECTORY="/migrations"

MYSQL_HOST="${DB_PRIMARY_HOST:?DB_PRIMARY_HOST is required}"
MYSQL_PORT="${DB_PRIMARY_PORT:-3306}"
MYSQL_DATABASE="${COMMUNITY_MYSQL_DATABASE:-community}"
MYSQL_USER="${COMMUNITY_MIGRATION_USERNAME:?COMMUNITY_MIGRATION_USERNAME is required}"
MYSQL_PASSWORD="${COMMUNITY_MIGRATION_PASSWORD:?COMMUNITY_MIGRATION_PASSWORD is required}"

if [[ "${MYSQL_DATABASE}" != "community" ]]; then
  echo "[community-migration] COMMUNITY_MYSQL_DATABASE must equal community" >&2
  exit 1
fi
if [[ ! "${MYSQL_PORT}" =~ ^[0-9]+$ ]] || (( MYSQL_PORT < 1 || MYSQL_PORT > 65535 )); then
  echo "[community-migration] DB_PRIMARY_PORT must be a valid TCP port" >&2
  exit 1
fi
if [[ ! -d "${MIGRATION_DIRECTORY}" ]]; then
  echo "[community-migration] migration directory is missing: ${MIGRATION_DIRECTORY}" >&2
  exit 1
fi

mysql_args=(
  --protocol=TCP
  --connect-timeout=15
  --default-character-set=utf8mb4
  --show-warnings
  "--host=${MYSQL_HOST}"
  "--port=${MYSQL_PORT}"
  "--user=${MYSQL_USER}"
  "${MYSQL_DATABASE}"
)

declare -A seen_versions=()
declare -A expected_checksums=()
declare -A expected_scripts=()
declare -a migration_files=()
declare -a migration_versions=()
declare -a migration_descriptions=()
declare -a migration_scripts=()
declare -a migration_checksums=()
declare -a expected_history=()
migration_count=0

for migration_file in "${MIGRATION_DIRECTORY}"/V[0-9][0-9][0-9]__*.sql; do
  if [[ ! -f "${migration_file}" ]]; then
    continue
  fi

  script_name="${migration_file##*/}"
  if [[ ! "${script_name}" =~ ^V([0-9]{3})__([a-z0-9_]+)\.sql$ ]]; then
    echo "[community-migration] invalid migration filename: ${script_name}" >&2
    exit 1
  fi
  version="${BASH_REMATCH[1]}"
  description="${BASH_REMATCH[2]}"
  if [[ -n "${seen_versions[${version}]:-}" ]]; then
    echo "[community-migration] duplicate migration version: ${version}" >&2
    exit 1
  fi
  seen_versions["${version}"]="${script_name}"

  checksum="$(sha256sum "${migration_file}" | awk '{print $1}')"
  expected_checksums["${version}"]="${checksum}"
  expected_scripts["${version}"]="${script_name}"
  migration_files+=("${migration_file}")
  migration_versions+=("${version}")
  migration_descriptions+=("${description}")
  migration_scripts+=("${script_name}")
  migration_checksums+=("${checksum}")
  expected_history+=("${version} ${script_name} ${checksum}")
  migration_count=$((migration_count + 1))
done

if (( migration_count == 0 )); then
  echo "[community-migration] no migrations found in ${MIGRATION_DIRECTORY}" >&2
  exit 1
fi

history_table_exists="$(MYSQL_PWD="${MYSQL_PASSWORD}" mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="select count(*) from information_schema.tables where table_schema = database() and table_name = 'community_forward_schema_history'")"
if [[ "${history_table_exists}" == "1" ]]; then
  existing_history_text="$(MYSQL_PWD="${MYSQL_PASSWORD}" mysql "${mysql_args[@]}" --batch --skip-column-names \
    --execute="select version, script, checksum_sha256 from community_forward_schema_history order by version")"
  if [[ -n "${existing_history_text}" ]]; then
    while IFS=$'\t' read -r installed_version installed_script installed_checksum; do
      if [[ -z "${expected_checksums[${installed_version}]:-}" \
          || "${expected_scripts[${installed_version}]}" != "${installed_script}" \
          || "${expected_checksums[${installed_version}]}" != "${installed_checksum}" ]]; then
        echo "[community-migration] installed history is not present unchanged in the reviewed migration set: ${installed_version}" >&2
        exit 1
      fi
    done <<<"${existing_history_text}"
  fi
elif [[ "${history_table_exists}" != "0" ]]; then
  echo "[community-migration] unable to determine migration history state" >&2
  exit 1
fi

for ((migration_index = 0; migration_index < migration_count; migration_index++)); do
  migration_file="${migration_files[${migration_index}]}"
  version="${migration_versions[${migration_index}]}"
  description="${migration_descriptions[${migration_index}]}"
  script_name="${migration_scripts[${migration_index}]}"
  checksum="${migration_checksums[${migration_index}]}"
  wrapper_file="$(mktemp)"
  trap 'rm -f "${wrapper_file:-}"' EXIT
  {
    printf "SET @community_migration_version = '%s';\n" "${version}"
    printf "SET @community_migration_description = '%s';\n" "${description}"
    printf "SET @community_migration_script = '%s';\n" "${script_name}"
    printf "SET @community_migration_checksum = '%s';\n" "${checksum}"
    printf 'SOURCE %s;\n' "${migration_file}"
  } >"${wrapper_file}"

  echo "[community-migration] applying ${script_name} (${checksum})"
  MYSQL_PWD="${MYSQL_PASSWORD}" mysql "${mysql_args[@]}" <"${wrapper_file}"
  rm -f "${wrapper_file}"
  trap - EXIT
done

expected_history_text="$(printf '%s\n' "${expected_history[@]}")"
actual_history_text="$(MYSQL_PWD="${MYSQL_PASSWORD}" mysql "${mysql_args[@]}" --batch --skip-column-names \
  --execute="select concat(version, ' ', script, ' ', checksum_sha256) from community_forward_schema_history order by version")"
if [[ "${expected_history_text}" != "${actual_history_text}" ]]; then
  echo "[community-migration] database history does not exactly match the reviewed migration set" >&2
  printf '[community-migration] expected history:\n%s\n' "${expected_history_text}" >&2
  printf '[community-migration] actual history:\n%s\n' "${actual_history_text}" >&2
  exit 1
fi

echo "[community-migration] ${migration_count} migration(s) validated/applied"
