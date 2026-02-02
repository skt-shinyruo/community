# Task List: 一致性缺口与依赖耦合治理（Perceived Consistency + RPC 收敛 + 幂等/配置护栏）

Directory: `helloagents/history/2026-02/202602021224_consistency_gap_solution/`

---

## 1. Perceived Consistency（前端 read-your-writes 优先）
- [√] 1.1 PostDetail 详情页点赞展示改为“以 SSOT 为准 + 短 TTL 覆盖”，避免刷新后短时间读到旧投影（`frontend/src/views/PostDetailView.vue`、`frontend/src/stores/postMetaCache.js`），verify why.md#requirement-perceived-consistency-read-your-writes-scenario-likeunlike-后立即看到一致的-likecount-与-liked
- [√] 1.2 搜索页增加“最终一致延迟提示 + 可观测引导”（不改变搜索后端语义，只提升用户预期管理）（`frontend/src/views/SearchView.vue`、`docs/SYSTEM_DESIGN.md`），verify why.md#requirement-perceived-consistency-read-your-writes-scenario-发帖编辑后搜索结果延迟可解释且可观测

## 2. RPC Aggregation Hardening（user 主页 fan-out 收敛）
- [√] 2.1 social-service：新增聚合 internal read API（一次返回 likeCount/followeeCount/followerCount/hasFollowed，可选 degraded 标记）（`social-service/src/main/java/com/nowcoder/community/social/api/InternalSocialReadController.java`、`social-service/src/main/java/com/nowcoder/community/social/api/dto/InternalUserProfileStatsResponse.java`），verify why.md#requirement-rpc-aggregation-hardening-降低-fan-out-scenario-用户主页不再串行多次调用-social-service
- [√] 2.2 user-service：SocialServiceClient 改为调用聚合接口，减少多次调用与尾延迟（`user-service/src/main/java/com/nowcoder/community/user/service/SocialServiceClient.java`），verify why.md#requirement-rpc-aggregation-hardening-降低-fan-out-scenario-用户主页不再串行多次调用-social-service
- [√] 2.3 user-service：UserController 使用聚合结果，并引入“降级可判定”字段（避免把依赖故障伪装成 0）（`user-service/src/main/java/com/nowcoder/community/user/api/UserController.java`、`user-service/src/main/java/com/nowcoder/community/user/api/dto/UserProfileResponse.java`），verify why.md#requirement-rpc-aggregation-hardening-降低-fan-out-scenario-用户主页不再串行多次调用-social-service

## 3. 私信写路径依赖放大控制（toName）
- [√] 3.1 message-service：对 username→userId resolve 增加短 TTL 缓存，降低重复回源（`message-service/src/main/java/com/nowcoder/community/message/service/UserServiceClient.java`），verify why.md#requirement-rpc-aggregation-hardening-降低-fan-out-scenario-私信发送使用-toname-时尽量不放大依赖
- [-] 3.2 frontend：尽量优先发送 toId（仅在首次输入用户名时 resolve），减少后端依赖压力（`frontend/src/views/ConversationDetailView.vue`、`frontend/src/api/services/userService.js`），verify why.md#requirement-rpc-aggregation-hardening-降低-fan-out-scenario-私信发送使用-toname-时尽量不放大依赖（原因：当前前端私信仅通过 conversationId 解析 toId 并发送，不存在 toName 输入入口）

## 4. Idempotency UX & Safety（幂等体验与 TTL 可配置）
- [√] 4.1 common：IdempotencyGuard 支持 TTL 配置化（processing/success），并优化缺失 key 的错误提示（`common/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyGuard.java`），verify why.md#requirement-idempotency-ux--safety-幂等体验与安全-scenario-非浏览器客户端可轻松正确使用幂等
- [√] 4.2 docs/scripts：补齐 curl 示例与脚本模板，降低第三方调用踩坑概率（`docs/SECURITY.md`、`scripts/curl-idempotent-post.sh`），verify why.md#requirement-idempotency-ux--safety-幂等体验与安全-scenario-非浏览器客户端可轻松正确使用幂等

## 5. Config Guardrails（配置护栏）
- [√] 5.1 scripts：新增配置自检脚本 doctor（不输出敏感值，输出缺失项与建议）（`scripts/doctor.sh`），verify why.md#requirement-config-guardrails-配置护栏-scenario-生产部署缺少-prod-profile-或关键密钥时可快速发现
- [√] 5.2 docs：补齐“配置矩阵/必配项”文档，并明确 prod profile 与 nacos fail-fast 的关系（`docs/DEPLOYMENT.md`、`docs/SECURITY.md`），verify why.md#requirement-config-guardrails-配置护栏-scenario-生产部署缺少-prod-profile-或关键密钥时可快速发现

## 6. Security Check
- [√] 6.1 安全检查：internal endpoint 权限、token 管理、allowlist、fail-open/fail-closed 策略复核（重点检查新增聚合 internal API 与 doctor 输出不泄露敏感信息）

## 7. Documentation Update（Knowledge Base）
- [√] 7.1 同步更新知识库：`helloagents/wiki/api.md`、`helloagents/wiki/arch.md`、相关 module 文档（`helloagents/wiki/modules/user.md`、`helloagents/wiki/modules/social.md`、`helloagents/wiki/modules/message.md`、`helloagents/wiki/modules/frontend.md`）
- [√] 7.2 更新 `helloagents/CHANGELOG.md`（记录本次一致性体验、RPC 收敛、幂等/配置护栏改动）

## 8. Testing
- [√] 8.1 social-service：聚合 internal API 单测（含 viewerId/降级标记）（`social-service/src/test/java/com/nowcoder/community/social/api/InternalSocialReadControllerTest.java`）
- [√] 8.2 user-service：用户主页聚合回归测试（避免多次调用与错误降级）（`user-service/src/test/java/com/nowcoder/community/user/service/SocialServiceClientTest.java`）
- [√] 8.3 frontend：PostDetail 点赞刷新一致性回归（store TTL 覆盖单测：`frontend/src/stores/postMetaCache.test.js`）
- [√] 8.4 common：IdempotencyGuard TTL 配置化单测（`common/src/test/java/com/nowcoder/community/common/idempotency/IdempotencyGuardTtlTest.java`）
