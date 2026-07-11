#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
retired='CurrentSchemaVersionFilter|schemaVersionOrCurrent|legacyCompatibleVersionFloor|RoomPersistedLegacyConsumer|RoomFanoutOwnerCoalescer|HttpRoomFanoutDispatcher|RoomFanoutTargetController|NoopRoomPresenceDirectory|/internal/im/realtime/fanout|owner-flush-interval|target-path|target-timeout|IM_ROOM_FANOUT_(MODE|TRANSPORT|OWNER_FLUSH_INTERVAL|TARGET_PATH|TARGET_TIMEOUT)|IM_ROOM_PRESENCE_ENABLED|worker-inbox-slot[^\n]*:0|events\.publisher|EVENTS_PUBLISHER|^[[:space:]]+publisher:[[:space:]]+(local|outbox-kafka)|projection\.(search\.post|growth\.task|user\.reward\.comment)|GrowthTaskProgressActionApi|MDC_KEY_LEGACY_TRACE_ID'
if rg -n "${retired}" "${REPO_ROOT}/backend" --glob '**/src/main/**' --glob '!**/target/**' \
    || rg -n "${retired}" \
        "${REPO_ROOT}/backend/community-app/src/test/resources" \
        "${REPO_ROOT}/backend/community-im/im-realtime/src/test/resources" \
    || rg -n "${retired}" \
        "${REPO_ROOT}/deploy/nacos" \
        "${REPO_ROOT}/deploy/compose.runtime.services.single.yml" \
        "${REPO_ROOT}/deploy/compose.runtime.services.cluster.yml" \
        "${REPO_ROOT}/docs/handbook"; then
  echo "retired compatibility surface remains" >&2
  exit 1
fi

im_yaml=(
  "${REPO_ROOT}/backend/community-im/im-realtime/src/main/resources/application.yml"
  "${REPO_ROOT}/backend/community-im/im-realtime/src/test/resources/application-test.yml"
  "${REPO_ROOT}/deploy/nacos/config/im-realtime.yaml"
)
retired_im_yaml='(?m)^  room-fanout:\r?\n(?:^    .*\r?\n)*?^    (mode|transport|owner-flush-interval|target-path|target-timeout):|^  room-presence:\r?\n(?:^    .*\r?\n)*?^    enabled:'
if rg -n -U "${retired_im_yaml}" "${im_yaml[@]}"; then
  echo "retired IM YAML switch remains" >&2
  exit 1
fi

rg -Fq 'projection.im.policy' "${REPO_ROOT}/deploy/nacos/config/community-kafka-policy.yaml"
rg -Fq 'projection.im.policy' "${REPO_ROOT}/docs/handbook"
for topic in content.events social.events user.events; do
  rg -Fq "${topic}" "${REPO_ROOT}/deploy/scripts/bootstrap-kafka-topics.sh"
  rg -Fq "${topic}" "${REPO_ROOT}/docs/handbook"
done
rg -Fq 'im.command.room-fanout-routed' "${REPO_ROOT}/deploy/nacos/config/im-realtime.yaml"
rg -Fq 'im.command.room-fanout-routed' "${REPO_ROOT}/deploy/scripts/bootstrap-kafka-topics.sh"
rg -Fq 'im.command.room-fanout-routed' "${REPO_ROOT}/docs/handbook"
rg -q 'schemaVersion.{0,12}1' "${REPO_ROOT}/docs/handbook/business-logic/im.md"
"${REPO_ROOT}/deploy/tests/topology_single_cluster.sh" >/dev/null
