#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../../../.." && pwd)"
retired='CurrentSchemaVersionFilter|schemaVersionOrCurrent|legacyCompatibleVersionFloor|RoomPersistedLegacyConsumer|RoomFanoutOwnerCoalescer|HttpRoomFanoutDispatcher|RoomFanoutTargetController|NoopRoomPresenceDirectory|/internal/im/realtime/fanout|owner-flush-interval|target-path|target-timeout|IM_ROOM_FANOUT_(MODE|TRANSPORT|OWNER_FLUSH_INTERVAL|TARGET_PATH|TARGET_TIMEOUT)|IM_ROOM_PRESENCE_ENABLED|worker-inbox-slot[^\n]*:0|events\.publisher|EVENTS_PUBLISHER|^[[:space:]]+publisher:[[:space:]]+(local|outbox-kafka)|projection\.(search\.post|growth\.task|user\.reward\.comment)|GrowthTaskProgressActionApi|MDC_KEY_LEGACY_TRACE_ID'
if rg -n "${retired}" "${REPO_ROOT}/backend" --glob '**/src/main/**' --glob '!**/target/**' \
    || rg -n "${retired}" \
        "${REPO_ROOT}/backend/community-app/src/test/resources" \
        "${REPO_ROOT}/backend/community-im/im-realtime/src/test/resources" \
    || rg -n "${retired}" \
        "${REPO_ROOT}/deploy/config/nacos" \
        "${REPO_ROOT}/deploy/compose/runtime/services/single.yml" \
        "${REPO_ROOT}/deploy/compose/runtime/services/cluster.yml" \
        "${REPO_ROOT}/docs/handbook"; then
  echo "retired compatibility surface remains" >&2
  exit 1
fi

if rg -n 'nacos/nacos-server:v2\.3\.2-slim|/nacos/actuator/health|-XX:-UseContainerSupport' "${REPO_ROOT}/deploy" \
    --glob '!deploy/tests/**'; then
  echo "retired Nacos 2.3.2 runtime or health endpoint remains" >&2
  exit 1
fi

current_schema="${REPO_ROOT}/deploy/database/business/current-state/010_current_schema.sql"
in_place_schema_change='information_schema|prepare[[:space:]]+stmt|deallocate[[:space:]]+prepare|alter[[:space:]]+table|drop[[:space:]]+(table|index)'
if rg -n -i "${in_place_schema_change}" "${current_schema}"; then
  echo "current-state snapshot still contains an in-place schema change" >&2
  exit 1
fi

mock_metadata_migration='show[[:space:]]+columns[[:space:]]+from[[:space:]]+ai_config|alter[[:space:]]+table[[:space:]]+ai_config|uk_ai_config_singleton|update[[:space:]]+ai_config[[:space:]]+set[[:space:]]+name.*where[[:space:]]+name|update[[:space:]]+ai_config[[:space:]]+set[[:space:]]+is_active.*where[[:space:]]+name'
if rg -n -i "${mock_metadata_migration}" \
    "${REPO_ROOT}/tools/mock-data-studio/src" \
    "${REPO_ROOT}/tools/mock-data-studio/test"; then
  echo "retired Mock Data Studio metadata migration remains" >&2
  exit 1
fi

rg -Fq '`request_hash` varchar(64)' "${current_schema}"
test "$(grep -Fc 'KEY `idx_outbox_status_updated` (`status`,`updated_at`,`id`)' "${current_schema}")" -eq 2
test "$(grep -Fc 'KEY `idx_outbox_status_created` (`status`,`created_at`,`id`)' "${current_schema}")" -eq 2

second_round_backend='content\.counter\.flush\.batch-size|im\.kafka\.consumer\.(group-id|auto-offset-reset)|numeric (userId|reportId) 已不再受支持'
if rg -n "${second_round_backend}" "${REPO_ROOT}/backend" --glob '**/src/main/**' --glob '!**/target/**' \
    || rg -n 'im\.kafka\.consumer\.(group-id|auto-offset-reset)' "${REPO_ROOT}/deploy/config/nacos" \
    || rg -n 'legacy-event-id-ignored' "${REPO_ROOT}/backend" --glob '!**/target/**'; then
  echo "second-round backend compatibility surface remains" >&2
  exit 1
fi

if rg -n 'createTime|occurredAt' \
    "${REPO_ROOT}/backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/LikePayload.java" \
    || rg -n 'entityUserId|postId' \
        "${REPO_ROOT}/backend/community-app/src/main/java/com/nowcoder/community/interaction/controller/dto/LikeRequest.java" \
    || rg -n 'entityUserId' \
        "${REPO_ROOT}/backend/community-app/src/main/java/com/nowcoder/community/social/controller/dto/FollowRequest.java" \
    || rg -n 'class[[:space:]]+UserSummary([[:space:]]|\{)' \
        "${REPO_ROOT}/backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto"; then
  echo "retired backend DTO compatibility field remains" >&2
  exit 1
fi

retired_idempotency='tryAcquireProcessing\(String operation, UUID userId, String key, Duration ttl\)|saveSuccess\(String operation, UUID userId, String key, String successJson, Duration ttl\)|"P"\.equals\(value\)|new Entry\(Status\.(PROCESSING|SUCCESS),[^,()]+\)'
if rg -n "${retired_idempotency}" \
    "${REPO_ROOT}/backend/community-common/common-idempotency/src" \
    "${REPO_ROOT}/backend/community-app/src" \
    --glob '!**/target/**'; then
  echo "retired idempotency contract remains" >&2
  exit 1
fi

like_repository="${REPO_ROOT}/backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/LikeRepository.java"
if rg -n 'default[[:space:]]+(long[[:space:]]+deleteLikesByEntity|List<LikeRelation>[[:space:]]+scanLikesByEntity)' "${like_repository}"; then
  echo "silent like cleanup repository default remains" >&2
  exit 1
fi

strict_contracts=(
  "${REPO_ROOT}/backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/RefreshTokenRepository.java"
  "${REPO_ROOT}/backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/PasswordResetTokenRepository.java"
  "${REPO_ROOT}/backend/community-app/src/main/java/com/nowcoder/community/user/domain/repository/UserRepository.java"
  "${REPO_ROOT}/backend/community-app/src/main/java/com/nowcoder/community/social/api/query/SocialBlockQueryApi.java"
  "${REPO_ROOT}/backend/community-app/src/main/java/com/nowcoder/community/user/api/query/UserModerationQueryApi.java"
)
if rg -n '\bdefault\b' "${strict_contracts[@]}"; then
  echo "silent default implementation remains in a strict contract" >&2
  exit 1
fi

if rg -n 'default[[:space:]]+long[[:space:]]+(next|current)BlockProjectionVersion' \
    "${REPO_ROOT}/backend/community-app/src/main/java/com/nowcoder/community/social/domain/repository/BlockRepository.java"; then
  echo "silent block projection version default remains" >&2
  exit 1
fi

frontend_retired='registrationToken[[:space:]]*\|\|[[:space:]]*userId|code[[:space:]]*===[[:space:]]*(10002|11001)|fulfillmentStatus|shipmentStatus|escrowStatus|fundState|fundsStatus|txn\?\.requestId|u\?\.userId|raw\.type[[:space:]]*\|\|[[:space:]]*raw\.entryType|deliveryModeSnapshot.*\|\|.*\.deliveryMode'
if rg -n "${frontend_retired}" "${REPO_ROOT}/frontend/src" --glob '!**/*.test.js'; then
  echo "retired frontend DTO compatibility surface remains" >&2
  exit 1
fi

if rg -n 'entityUserId|postId' "${REPO_ROOT}/frontend/src/api/services/socialService.js"; then
  echo "retired social write DTO field remains in the frontend API client" >&2
  exit 1
fi

comment_page_files=(
  "${REPO_ROOT}/frontend/src/api/services/postService.js"
  "${REPO_ROOT}/frontend/src/views/post-detail/usePostDetailLoader.js"
)
if rg -n 'if[[:space:]]*\(Array\.isArray\(raw\)\)' "${comment_page_files[@]}"; then
  echo "legacy bare-array comment page remains" >&2
  exit 1
fi

im_yaml=(
  "${REPO_ROOT}/backend/community-im/im-realtime/src/main/resources/application.yml"
  "${REPO_ROOT}/backend/community-im/im-realtime/src/test/resources/application-test.yml"
  "${REPO_ROOT}/deploy/config/nacos/im-realtime.yaml"
)
retired_im_yaml='(?m)^  room-fanout:\r?\n(?:^    .*\r?\n)*?^    (mode|transport|owner-flush-interval|target-path|target-timeout):|^  room-presence:\r?\n(?:^    .*\r?\n)*?^    enabled:'
if rg -n -U "${retired_im_yaml}" "${im_yaml[@]}"; then
  echo "retired IM YAML switch remains" >&2
  exit 1
fi

rg -Fq 'projection.im.policy' "${REPO_ROOT}/deploy/config/nacos/community-kafka-policy.yaml"
rg -Fq 'projection.im.policy' "${REPO_ROOT}/docs/handbook"
for topic in content.events social.events user.events; do
  rg -Fq "${topic}" "${REPO_ROOT}/deploy/scripts/bootstrap-kafka-topics.sh"
  rg -Fq "${topic}" "${REPO_ROOT}/docs/handbook"
done
rg -Fq 'im.command.room-fanout-routed' "${REPO_ROOT}/deploy/config/nacos/im-realtime.yaml"
rg -Fq 'im.command.room-fanout-routed' "${REPO_ROOT}/deploy/scripts/bootstrap-kafka-topics.sh"
rg -Fq 'im.command.room-fanout-routed' "${REPO_ROOT}/docs/handbook"
rg -q 'schemaVersion.{0,12}1' "${REPO_ROOT}/docs/handbook/business-logic/im.md"
"${REPO_ROOT}/deploy/tests/contracts/compose/topology_single_cluster.sh" >/dev/null
"${REPO_ROOT}/deploy/tests/contracts/database/nacos_schema_contract.sh" >/dev/null
