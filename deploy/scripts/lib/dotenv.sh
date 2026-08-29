# Shared dotenv helpers, sourced by deployment.sh and render-backend-env.sh.
# Callers must set ENV_FILE to the env file being parsed before using these helpers.

read_dotenv_value() {
  local variable="$1"
  local file="$2"

  awk -v variable="${variable}" '
    /^[[:space:]]*(#|$)/ { next }
    {
      line = $0
      sub(/\r$/, "", line)
      prefix = "^[[:space:]]*(export[[:space:]]+)?" variable "[[:space:]]*="
      if (line ~ prefix) {
        sub(prefix, "", line)
        sub(/^[[:space:]]+/, "", line)
        sub(/[[:space:]]+$/, "", line)
        if (length(line) >= 2) {
          first = substr(line, 1, 1)
          last = substr(line, length(line), 1)
          if ((first == "\"" && last == "\"") || (first == "\047" && last == "\047")) {
            line = substr(line, 2, length(line) - 2)
          }
        }
        value = line
        found = 1
      }
    }
    END {
      if (!found) exit 1
      print value
    }
  ' "${file}"
}

resolve_process_env_then_dotenv_then_fallback() {
  local variable="$1"
  local fallback="${2:-}"
  local resolved

  if [[ -v "${variable}" ]]; then
    resolved="${!variable}"
  elif resolved="$(read_dotenv_value "${variable}" "${ENV_FILE}")"; then
    :
  else
    resolved="${fallback}"
  fi
  printf '%s' "${resolved}"
}
