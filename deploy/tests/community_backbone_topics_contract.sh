#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
BOOTSTRAP="${REPO_ROOT}/deploy/scripts/bootstrap-kafka-topics.sh"

partition_count() {
  local wanted="$1"
  awk -v wanted="${wanted}" '
    {
      topic = ""
      partitions = ""
      for (i = 1; i <= NF; i++) {
        if ($i == "--topic") topic = $(i + 1)
        if ($i == "--partitions") partitions = $(i + 1)
      }
      if (topic == wanted) {
        count++
        value = partitions
      }
    }
    END {
      if (count != 1 || value == "") exit 1
      print value
    }
  ' "${BOOTSTRAP}"
}

for topic in content.events social.events user.events; do
  test "$(partition_count "${topic}")" = "12"
  test "$(partition_count "${topic}.dlq")" = "12"
done
