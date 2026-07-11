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

pairs=(
  im.event.private-persisted=12
  im.event.room-persisted=12
  im.event.private-committed=12
  im.event.room-committed=12
  im.event.private-rejected=12
  im.event.room-rejected=12
  im.event.room-member-changed=3
  im.event.user-messaging-policy-changed=12
  im.event.user-block-relation-changed=12
  im.command.room-fanout-routed=64
)

for pair in "${pairs[@]}"; do
  topic="${pair%%=*}"
  expected="${pair##*=}"
  test "$(partition_count "${topic}")" = "${expected}"
  test "$(partition_count "${topic}.dlq")" = "${expected}"
done
