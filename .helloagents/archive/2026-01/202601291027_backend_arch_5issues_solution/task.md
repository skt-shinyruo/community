# Task List: 后端架构治理（5 类系统性问题 + 扩展治理项）

Directory: `.helloagents/archive/2026-01/202601291027_backend_arch_5issues_solution/`

---

## 0. 验收口径（统一核心问题 + 扩展项的可验证标准）
- [√] 0.1 补齐“最终一致窗口 / 安全默认态 / 错误协议”验收口径到 `docs/SYSTEM_DESIGN.md`，verify why.md#requirement-r1-写路径跨服务同步耦合治理（最终一致）
- [√] 0.2 同步更新架构 SSOT（能力矩阵 + 默认值）到 `.helloagents/project.md`，verify why.md#requirement-r5-环境差异与-ssot-修复

## 1. R2 错误协议统一（HTTP status + Result.code）【先做，作为全链路基线】
- [√] 1.1 为 `ErrorCode` 增加“HTTP status 映射能力”（接口或映射表），并补齐 `DataAccessException -> 503` 等依赖故障语义，files: `common/src/main/java/com/nowcoder/community/common/api/ErrorCode.java`, `common/src/main/java/com/nowcoder/community/common/web/GlobalExceptionHandler.java`, verify why.md#requirement-r2-错误协议统一（http-status--resultcode）
- [√] 1.2 为 `AuthErrorCode` 等业务码补齐 HTTP status 映射（401/403/400 等），files: `common/src/main/java/com/nowcoder/community/common/api/AuthErrorCode.java`, `common/src/main/java/com/nowcoder/community/common/exception/BusinessException.java`, verify why.md#scenario-登录失败（业务码）与鉴权失败（通用码）一致呈现
- [√] 1.3 统一 Servlet Security 异常输出与 status（保持 `Result` 结构不变），files: `common/src/main/java/com/nowcoder/community/common/web/SecurityExceptionHandler.java`, `common/src/main/java/com/nowcoder/community/common/api/Result.java`, verify why.md#requirement-r2-错误协议统一（http-status--resultcode）
- [√] 1.4 统一 gateway Reactive 的 401/403 错误输出策略（HTTP status + `Result`），files: `gateway/src/main/java/com/nowcoder/community/gateway/config/GatewaySecurityConfig.java`, `gateway/src/main/java/com/nowcoder/community/gateway/config/ReactiveSecurityExceptionHandler.java`, verify why.md#requirement-r2-错误协议统一（http-status--resultcode）
- [√] 1.5 前端 Axios：对非 2xx 响应仍解析 `Result` 并展示 message/traceId（同时保证 401 refresh 逻辑不误触发），files: `frontend/src/api/http.js`, `frontend/src/stores/auth.js`, verify why.md#requirement-r2-错误协议统一（http-status--resultcode）

## 2. R4 内部调用治理（internal client 语义保真 + token 分域）
- [√] 2.1 为 internal RestTemplate 统一“非 2xx 也读取 body”的错误处理器/拦截器，files: `common/src/main/java/com/nowcoder/community/common/web/internalclient/InternalClientSupport.java`, `common/src/main/java/com/nowcoder/community/common/web/TraceIdClientHttpRequestInterceptor.java`, verify why.md#scenario-内部调用错误语义保真
- [√] 2.2 统一 user-service → social-service internal client 的错误映射与降级语义（区分 degraded/error/timeout），files: `user-service/src/main/java/com/nowcoder/community/user/service/SocialServiceClient.java`, `user-service/src/main/java/com/nowcoder/community/user/config/UserRestClientConfig.java`, verify why.md#requirement-r4-internal-token-分域与内部调用治理
- [√] 2.3 统一 content-service 内部调用（UserModerationClient/SocialBlockClient）的错误映射口径（为后续“投影替换”做过渡），files: `content-service/src/main/java/com/nowcoder/community/content/service/UserModerationClient.java`, `content-service/src/main/java/com/nowcoder/community/content/service/SocialBlockClient.java`, verify why.md#requirement-r1-写路径跨服务同步耦合治理（最终一致）
- [√] 2.4 去除 internal-token 的“全局 token 兜底”默认路径，并加强路径 segment 解析（禁止空 segment 绕过），files: `common/src/main/java/com/nowcoder/community/common/internal/InternalTokenFilter.java`, verify why.md#scenario-internal-token-必须分域（避免全局-token-扩大爆炸半径）

## 3. R3 网关默认安全态（fail-closed）与可信代理边界
- [√] 3.1 生产配置：Origin Guard allowlist 漏配默认拒绝，files: `gateway/src/main/resources/application.yml`, `deploy/nacos-config/gateway.yaml`, verify why.md#scenario-origin-allowlist-漏配错误配置
- [√] 3.2 生产配置：限流 Redis 故障默认 fail-closed（返回 503），files: `gateway/src/main/resources/application.yml`, `gateway/src/main/java/com/nowcoder/community/gateway/filter/GatewayRateLimitGlobalFilter.java`, verify why.md#scenario-限流依赖异常（redis-不可用）
- [√] 3.3 IP 解析：仅在可信代理启用且 CIDR 明确时信任 XFF，files: `gateway/src/main/java/com/nowcoder/community/gateway/filter/ClientIpResolver.java`, `gateway/src/main/java/com/nowcoder/community/gateway/config/TrustedProxyProperties.java`, verify why.md#scenario-ip-可信边界（x-forwarded-for）
- [√] 3.4 统计采集隔离：确保 analytics 采集不影响转发链路（必要时改为异步事件/日志驱动），files: `gateway/src/main/java/com/nowcoder/community/gateway/filter/AnalyticsCollectGlobalFilter.java`, `analytics-service/src/main/java/com/nowcoder/community/analytics/api/AnalyticsController.java`, verify why.md#requirement-r3-网关默认安全态（fail-closed）与职责收敛

## 4. R1 写路径解耦（最终一致）：本地投影 + 事件同步
- [√] 4.1 设计并落地“用户处罚状态变更事件”契约（type/payload/topic），files: `common/src/main/java/com/nowcoder/community/common/event/EventTypes.java`, `common/src/main/java/com/nowcoder/community/common/event/payload/ModerationPayload.java`, verify why.md#requirement-r1-写路径跨服务同步耦合治理（最终一致）
- [√] 4.2 user-service：新增处罚状态变更事件的消费/生产（建议 outbox），files: `user-service/src/main/java/com/nowcoder/community/user/kafka/ModerationEventConsumer.java`, `user-service/src/main/java/com/nowcoder/community/user/service/InternalUserService.java`, verify why.md#scenario-状态变更传播与最终一致窗口
- [√] 4.3 设计并落地“拉黑关系变更事件”契约（新增 payload），files: `common/src/main/java/com/nowcoder/community/common/event/EventTypes.java`, `common/src/main/java/com/nowcoder/community/common/event/payload/BlockPayload.java`, verify why.md#scenario-拉黑关系写入拦截（不依赖-social-service-实时可用）
- [√] 4.4 social-service：发布拉黑关系变更事件（建议 outbox），files: `social-service/src/main/java/com/nowcoder/community/social/event/KafkaSocialEventPublisher.java`, `social-service/src/main/java/com/nowcoder/community/social/block/BlockService.java`, verify why.md#requirement-r1-写路径跨服务同步耦合治理（最终一致）
- [√] 4.5 content-service：新增处罚/拉黑本地投影存储（表或 Redis），files: `content-service/src/main/java/com/nowcoder/community/content/projection/UserModerationProjectionRepository.java`, `deploy/mysql-init/020_schema_content.sql`, verify why.md#scenario-禁言封禁用户写入拦截（不依赖-user-service-实时可用）
- [√] 4.6 content-service：消费事件更新投影，并将写路径校验切换为“只读本地投影”，files: `content-service/src/main/java/com/nowcoder/community/content/kafka/ModerationEventConsumer.java`, `content-service/src/main/java/com/nowcoder/community/content/service/CommentService.java`, verify why.md#requirement-r1-写路径跨服务同步耦合治理（最终一致）
- [√] 4.7 message-service：新增处罚/拉黑本地投影并切换私信写路径校验，files: `message-service/src/main/java/com/nowcoder/community/message/kafka/ModerationEventConsumer.java`, `message-service/src/main/java/com/nowcoder/community/message/service/PrivateMessageService.java`, verify why.md#scenario-拉黑关系写入拦截（不依赖-social-service-实时可用）
- [√] 4.8 对账/回填：提供 internal 扫描接口与定时纠偏 job，files: `user-service/src/main/java/com/nowcoder/community/user/api/InternalUserController.java`, `content-service/src/main/java/com/nowcoder/community/content/projection/ProjectionReconcileJob.java`, verify why.md#scenario-状态变更传播与最终一致窗口
- [√] 4.9 治理处罚事务边界修复：禁止在本地事务内调用 user-service 执行 mute/ban，改为 after-commit/outbox 投递命令并提供补偿回放，files: `content-service/src/main/java/com/nowcoder/community/content/service/ModerationService.java`, `content-service/src/main/java/com/nowcoder/community/content/event/KafkaContentEventPublisher.java`, verify why.md#scenario-处罚动作（mute/ban）不在-db-事务内执行跨服务副作用

## 5. R5 环境一致性与 SSOT 修复（以 search 为代表）
- [√] 5.1 明确并收敛 `search.storage` 的默认值（避免配置缺失隐式退化到 memory），files: `search-service/src/main/java/com/nowcoder/community/search/repo/InMemoryPostSearchRepository.java`, `search-service/src/main/resources/application.yml`, verify why.md#scenario-search-存储后端一致性与可预期切换
- [√] 5.2 更新部署配置与文档，使默认值/部署/SSOT 三者一致，files: `deploy/nacos-config/search-service.yaml`, `docs/ARCHITECTURE.md`, verify why.md#requirement-r5-环境差异与-ssot-修复
- [√] 5.3 为 search 切换补齐契约/集成测试（至少覆盖分页/排序/高亮差异的约束），files: `search-service/src/test/java/com/nowcoder/community/search/api/SearchControllerTest.java`, `.helloagents/modules/search.md`, verify why.md#requirement-r5-环境差异与-ssot-修复

## 6. R6 search-service 事件消费幂等可靠性（避免丢索引更新）
- [√] 6.1 修复幂等表写入异常处理：仅将 DuplicateKey 视为重复，其余异常必须触发重试/DLQ，files: `search-service/src/main/java/com/nowcoder/community/search/kafka/SearchConsumedEventStore.java`, verify why.md#scenario-幂等表写入异常时不应吞掉并跳过消息
- [√] 6.2 调整幂等点位：ES upsert/delete 成功后再写入 consumed 表，确保 ES 故障可安全重试，files: `search-service/src/main/java/com/nowcoder/community/search/kafka/PostEventConsumer.java`, verify why.md#scenario-es-写入失败后可安全重试
- [√] 6.3 明确“不支持版本/类型”的丢弃策略：不写入 consumed 表或单独记录 skipped，避免未来升级后无法回放，files: `search-service/src/main/java/com/nowcoder/community/search/kafka/PostEventConsumer.java`, `docs/SYSTEM_DESIGN.md`, verify why.md#requirement-r6-search-service-事件消费幂等可靠性（避免丢索引更新）
- [√] 6.4 增加回归测试：模拟 ES 写入失败→恢复，事件应最终反映到索引，files: `search-service/src/test/java/com/nowcoder/community/search/kafka/PostEventConsumerTest.java`, `search-service/src/main/java/com/nowcoder/community/search/kafka/PostEventConsumer.java`, verify why.md#scenario-es-写入失败后可安全重试

## 7. R7 social-service DB 异常治理（禁止 silent failure）
- [√] 7.1 Like 持久化：仅吞唯一约束冲突，其余 DB 异常应显式失败并可告警，files: `social-service/src/main/java/com/nowcoder/community/social/like/DbLikeRepository.java`, verify why.md#scenario-db-故障时不应伪装为“未点赞未关注数量为-0”
- [√] 7.2 Follow 持久化：仅吞唯一约束冲突，其余 DB 异常应显式失败并可告警，files: `social-service/src/main/java/com/nowcoder/community/social/follow/DbFollowRepository.java`, verify why.md#scenario-db-故障时不应伪装为“未点赞未关注数量为-0”
- [√] 7.3 Block 持久化：仅吞唯一约束冲突，其余 DB 异常应显式失败并可告警，files: `social-service/src/main/java/com/nowcoder/community/social/block/DbBlockRepository.java`, verify why.md#scenario-db-故障时不应伪装为“未点赞未关注数量为-0”

## 8. R8 敏感配置兜底治理（占位默认值 + 全局 token）
- [√] 8.1 清理占位默认值与全局 token 兜底（gateway + auth），files: `deploy/nacos-config/gateway.yaml`, `deploy/nacos-config/auth-service.yaml`, verify why.md#scenario-生产环境缺失密钥时必须失败（而非使用占位默认值）
- [√] 8.2 清理占位默认值与全局 token 兜底（content + user），files: `deploy/nacos-config/content-service.yaml`, `deploy/nacos-config/user-service.yaml`, verify why.md#scenario-internal-token-必须分域（避免全局-token-扩大爆炸半径）
- [√] 8.3 清理占位默认值与全局 token 兜底（social + message），files: `deploy/nacos-config/social-service.yaml`, `deploy/nacos-config/message-service.yaml`, verify why.md#scenario-internal-token-必须分域（避免全局-token-扩大爆炸半径）
- [√] 8.4 清理占位默认值与全局 token 兜底（search + analytics），files: `deploy/nacos-config/search-service.yaml`, `deploy/nacos-config/analytics-service.yaml`, verify why.md#scenario-生产环境缺失密钥时必须失败（而非使用占位默认值）
- [√] 8.5 为本地/测试补齐配置指引与示例（避免移除兜底后启动困难），files: `deploy/README.md`, `deploy/.env.example`, verify why.md#requirement-r8-敏感配置占位默认值与全局兜底治理
- [√] 8.6 清理各服务 `application.yml` 与 internal client 的 `${...:${INTERNAL_TOKEN:}}` 兜底（仅允许按服务 token），并同步脚本与文档，files: `auth-service/src/main/resources/application.yml`, `content-service/src/main/resources/application.yml`, `content-service/src/main/java/com/nowcoder/community/content/service/UserModerationClient.java`, `content-service/src/main/java/com/nowcoder/community/content/service/SocialBlockClient.java`, `message-service/src/main/resources/application.yml`, `message-service/src/main/java/com/nowcoder/community/message/service/UserServiceClient.java`, `message-service/src/main/java/com/nowcoder/community/message/service/SocialServiceClient.java`, `message-service/src/main/java/com/nowcoder/community/message/service/UserModerationClient.java`, `user-service/src/main/resources/application.yml`, `search-service/src/main/resources/application.yml`, `analytics-service/src/main/resources/application.yml`, `social-service/src/main/resources/application.yml`, `gateway/src/main/resources/application.yml`, `scripts/search-reindex.sh`, `.helloagents/modules/auth-service.md`, verify why.md#scenario-internal-token-必须分域（避免全局-token-扩大爆炸半径）

## 9. R9 登录态存储与会话安全基线（refresh token / cookie）
- [√] 9.1 refresh token 存储默认切换到 Redis（或非 dev 环境缺失配置即失败），files: `auth-service/src/main/java/com/nowcoder/community/auth/service/RedisRefreshTokenStore.java`, `auth-service/src/main/java/com/nowcoder/community/auth/service/InMemoryRefreshTokenStore.java`, verify why.md#scenario-auth-service-多实例重启后-refresh-token-仍可用
- [√] 9.2 部署配置显式启用 Redis refresh store 并补齐相关 Redis 配置，files: `deploy/nacos-config/auth-service.yaml`, verify why.md#scenario-auth-service-多实例重启后-refresh-token-仍可用
- [√] 9.3 生产环境 refresh cookie 强制 Secure=true，并明确 SameSite 策略为显式配置项，files: `auth-service/src/main/resources/application.yml`, `deploy/nacos-config/auth-service.yaml`, verify why.md#scenario-refresh-cookie-在生产-https-下必须安全
- [√] 9.4 前端与网关联动验证：refresh 流程在非 2xx 错误协议与 cookie 策略下仍可用，files: `frontend/src/api/http.js`, `gateway/src/main/java/com/nowcoder/community/gateway/filter/OriginGuardGlobalFilter.java`, verify why.md#requirement-r9-登录态存储与会话安全基线（refresh-token--cookie）

## 10. R10 跨服务枚举/常量 SSOT（entityType/targetType）
- [√] 10.1 在 common 增加统一枚举/常量定义并写入 SSOT 文档，files: `common/src/main/java/com/nowcoder/community/common/domain/EntityTypes.java`, `docs/SYSTEM_DESIGN.md`, verify why.md#requirement-r10-跨服务枚举常量-ssot（entitytypetargettype）
- [√] 10.2 将 content-service 的魔法数字迁移为 common 常量并补齐兼容校验，files: `content-service/src/main/java/com/nowcoder/community/content/service/CommentService.java`, `content-service/src/main/java/com/nowcoder/community/content/kafka/SocialEventConsumer.java`, `content-service/src/main/java/com/nowcoder/community/content/api/PostController.java`, `content-service/src/main/java/com/nowcoder/community/content/like/RedisLikeQueryService.java`, `content-service/src/main/java/com/nowcoder/community/content/service/ReportService.java`, verify why.md#scenario-entitytypetargettype-值在全链路一致且可演进
- [√] 10.3 将 social-service 的魔法数字迁移为 common 常量并补齐兼容校验，files: `social-service/src/main/java/com/nowcoder/community/social/follow/FollowController.java`, `social-service/src/main/java/com/nowcoder/community/social/like/LikeController.java`, verify why.md#scenario-entitytypetargettype-值在全链路一致且可演进
- [√] 10.4 将 user-service 的魔法数字迁移为 common 常量并补齐兼容校验，files: `user-service/src/main/java/com/nowcoder/community/user/service/SocialServiceClient.java`, verify why.md#scenario-事件载荷中的枚举值可验证

## 11. R11 聚合接口 N+1 治理（DB + RPC）
- [√] 11.1 message-service：将会话列表的 per-item 计数查询改为批量 SQL，files: `message-service/src/main/java/com/nowcoder/community/message/dao/MessageMapper.java`, `message-service/src/main/java/com/nowcoder/community/message/service/PrivateMessageService.java`, verify why.md#scenario-会话列表不触发-n1-db-查询
- [√] 11.2 user-service：提供批量用户摘要 internal API（按 ids 返回 user summary），files: `user-service/src/main/java/com/nowcoder/community/user/api/InternalUserController.java`, `user-service/src/main/java/com/nowcoder/community/user/service/InternalUserService.java`, verify why.md#scenario-会话列表不触发-n1-跨服务用户查询
- [√] 11.3 message-service：改为调用 user-service 的批量 internal API（并用 internal-token），files: `message-service/src/main/java/com/nowcoder/community/message/service/UserServiceClient.java`, `deploy/nacos-config/message-service.yaml`, verify why.md#scenario-会话列表不触发-n1-跨服务用户查询

## 12. R12 事件版本治理一致化（envelope version + unknown handling）
- [√] 12.1 在 common 提供统一的 envelope 解析与校验工具（version/type/required fields），files: `common/src/main/java/com/nowcoder/community/common/event/EventEnvelope.java`, `common/src/main/java/com/nowcoder/community/common/event/EventEnvelopeParser.java`, verify why.md#scenario-消费端对-envelope-version-统一校验
- [√] 12.2 统一 message-service/user-service/content-service 对 version/type 的处理策略（DLQ/skip 可配置 + 不产生 DLQ 噪音），files: `message-service/src/main/java/com/nowcoder/community/message/kafka/NoticeEventProcessor.java`, `message-service/src/main/java/com/nowcoder/community/message/kafka/ModerationEventConsumer.java`, `user-service/src/main/java/com/nowcoder/community/user/kafka/PointsEventConsumer.java`, `user-service/src/main/java/com/nowcoder/community/user/kafka/ModerationEventConsumer.java`, `content-service/src/main/java/com/nowcoder/community/content/kafka/SocialEventConsumer.java`, `content-service/src/main/java/com/nowcoder/community/content/kafka/ModerationEventConsumer.java`, verify why.md#scenario-未知-type-的处理策略可控且可观测
- [√] 12.3 文档与 runbook：补齐事件演进与 unknown handling 约定，files: `docs/SYSTEM_DESIGN.md`, `.helloagents/modules/common.md`, verify why.md#requirement-r12-事件版本治理一致化（envelope-version--unknown-handling）

## 13. R13 运维/管理入口统一（降低攻击面）
- [√] 13.1 search-service：收敛 reindex 入口，明确只保留 `/internal/search/reindex`（或仅保留 gateway 入口），files: `search-service/src/main/java/com/nowcoder/community/search/api/SearchController.java`, `search-service/src/main/java/com/nowcoder/community/search/api/InternalSearchController.java`, verify why.md#scenario-reindex-等高风险运维能力只有一个外部入口
- [√] 13.2 gateway：提供管理员触发的 reindex 入口并以 internal-token 调用下游 internal API，files: `gateway/src/main/java/com/nowcoder/community/gateway/filter/AuditLogGlobalFilter.java`, `gateway/src/main/resources/application.yml`, verify why.md#scenario-运维入口具备审计限流与可回滚
- [√] 13.3 限流与开关：为运维入口增加更严格限流与可关闭配置项，files: `gateway/src/main/java/com/nowcoder/community/gateway/config/GatewayRateLimitProperties.java`, `deploy/nacos-config/gateway.yaml`, verify why.md#scenario-运维入口具备审计限流与可回滚

## 14. R14 配置中心与启动策略（生产 fail-closed）
- [√] 14.1 common：新增启动期校验组件（非 dev 缺失关键配置即失败），并输出缺失项清单与修复指引，files: `common/src/main/java/com/nowcoder/community/common/startup/StartupValidation.java`, `common/src/main/java/com/nowcoder/community/common/startup/StartupValidationAutoConfig.java`, verify why.md#scenario-关键配置缺失必须被启动期校验阻断
- [√] 14.2 gateway：在 prod profile 下将 nacos 配置导入改为必需（required/fail-fast）并补齐关键配置校验（避免 optional 退化），files: `gateway/src/main/resources/application-prod.yml`, `gateway/src/main/resources/application.yml`, verify why.md#scenario-nacos-不可用配置缺失时生产不得静默退化启动
- [√] 14.3 auth-service：新增 `application-prod.yml` 并在 prod profile 下将 nacos 配置导入改为必需（required/fail-fast），files: `auth-service/src/main/resources/application-prod.yml`, `auth-service/src/main/resources/application.yml`, verify why.md#scenario-nacos-不可用配置缺失时生产不得静默退化启动
- [√] 14.4 content-service：新增 `application-prod.yml` 并在 prod profile 下将 nacos 配置导入改为必需（required/fail-fast），files: `content-service/src/main/resources/application-prod.yml`, `content-service/src/main/resources/application.yml`, verify why.md#scenario-nacos-不可用配置缺失时生产不得静默退化启动
- [√] 14.5 message-service：新增 `application-prod.yml` 并在 prod profile 下将 nacos 配置导入改为必需（required/fail-fast），files: `message-service/src/main/resources/application-prod.yml`, `message-service/src/main/resources/application.yml`, verify why.md#scenario-nacos-不可用配置缺失时生产不得静默退化启动
- [√] 14.6 user-service：新增 `application-prod.yml` 并在 prod profile 下将 nacos 配置导入改为必需（required/fail-fast），files: `user-service/src/main/resources/application-prod.yml`, `user-service/src/main/resources/application.yml`, verify why.md#scenario-nacos-不可用配置缺失时生产不得静默退化启动
- [√] 14.7 social-service/search-service/analytics-service：分别新增 `application-prod.yml` 并在 prod profile 下将 nacos 配置导入改为必需（required/fail-fast），files: `social-service/src/main/resources/application-prod.yml`, `search-service/src/main/resources/application-prod.yml`, verify why.md#scenario-nacos-不可用配置缺失时生产不得静默退化启动

## 15. R15 可观测性端点治理（actuator/prometheus）
- [√] 15.1 选型并固化指标抓取授权方案（推荐 Basic Auth），同时更新 Prometheus 抓取配置，files: `deploy/observability/prometheus.yml`, `deploy/docker-compose.yml`, verify why.md#scenario-prometheus-可以稳定抓取指标（不依赖-jwt）
- [√] 15.2 gateway（Reactive）：为 `/actuator/prometheus` 增加与选型一致的保护策略（并避免 permitAll(/actuator/**) 漂移），files: `gateway/src/main/java/com/nowcoder/community/gateway/config/GatewaySecurityConfig.java`, `gateway/src/main/resources/application.yml`, verify why.md#scenario-actuator-暴露面可控且可审计
- [√] 15.3 auth-service：收敛 `/actuator/**` 的放行范围（仅 health/info/prometheus），并按选型增加保护策略，files: `auth-service/src/main/java/com/nowcoder/community/auth/config/AuthSecurityConfig.java`, `auth-service/src/main/resources/application.yml`, verify why.md#scenario-actuator-暴露面可控且可审计
- [√] 15.4 其余 Servlet 服务：统一放行/保护 `/actuator/prometheus`（与 Prometheus 配置一致），files: `content-service/src/main/java/com/nowcoder/community/content/config/ContentSecurityConfig.java`, `message-service/src/main/java/com/nowcoder/community/message/config/MessageSecurityConfig.java`, verify why.md#scenario-prometheus-可以稳定抓取指标（不依赖-jwt）

## 16. R16 Kafka DLQ fail-closed 与标准化
- [√] 16.1 common：下沉统一 DLQ schema（含 traceId）与发布器（可确认发送，失败抛出触发 fail-closed），files: `common/src/main/java/com/nowcoder/community/common/kafka/dlq/KafkaDlqRecord.java`, `common/src/main/java/com/nowcoder/community/common/kafka/dlq/KafkaDlqPublisher.java`, verify why.md#scenario-dlq-载荷具备统一-schema-与-traceid
- [√] 16.2 message-service：改造 `KafkaErrorHandlerConfig` 使用统一发布器并在 DLQ 发布失败时 fail-closed，files: `message-service/src/main/java/com/nowcoder/community/message/kafka/KafkaErrorHandlerConfig.java`, `message-service/src/main/java/com/nowcoder/community/message/kafka/KafkaDlqRecord.java`, verify why.md#scenario-消费失败后进入-dlq-且不丢失
- [√] 16.3 content-service/search-service：改造 `KafkaErrorHandlerConfig` 使用统一发布器并在 DLQ 发布失败时 fail-closed，files: `content-service/src/main/java/com/nowcoder/community/content/kafka/KafkaErrorHandlerConfig.java`, `search-service/src/main/java/com/nowcoder/community/search/kafka/KafkaErrorHandlerConfig.java`, verify why.md#scenario-消费失败后进入-dlq-且不丢失

## 17. R17 环境 profile 与配置覆盖治理（确保 prod 策略生效）
- [√] 17.1 部署入口强制启用 prod profile（避免 dev 默认值误用），files: `deploy/docker-compose.yml`, `deploy/README.md`, verify why.md#scenario-生产环境必须显式启用-prod-profile
- [√] 17.2 为 Nacos 配置补齐 profile 策略与命名约定（如 `{service}-prod.yaml` 可选覆盖），并写入 SSOT，files: `deploy/nacos-config/README.md`, `docs/SYSTEM_DESIGN.md`, verify why.md#scenario-生产环境必须显式启用-prod-profile
- [√] 17.3 把“关键 fail-closed 默认值”从“仅 prod 文件存在”升级为“可验证兜底 + 启动期校验”（与 R14 联动），files: `gateway/src/main/resources/application-prod.yml`, `.helloagents/modules/runbooks/deploy-prod.md`, verify why.md#scenario-关键-fail-closed-默认值不能依赖“只在-prod-profile-才存在的文件”

## 18. R18 防旁路与 cookie 安全边界固化（OriginGuard/CSRF）
- [√] 18.1 auth-service：实现服务侧 OriginGuard（覆盖 login/refresh/logout），与 gateway allowlist 配置对齐，files: `auth-service/src/main/java/com/nowcoder/community/auth/web/AuthOriginGuardFilter.java`, `auth-service/src/main/resources/application.yml`, verify why.md#scenario-auth-service-被旁路访问时仍不降低安全性
- [√] 18.2 gateway：补齐“旁路禁止”的部署验收项与说明（只暴露 gateway/ingress），files: `deploy/docker-compose.ports.yml`, `.helloagents/modules/runbooks/security.md`, verify why.md#scenario-auth-service-被旁路访问时仍不降低安全性
- [√] 18.3 明确 SameSite/Secure/allowlist 的组合策略并写入 SSOT（含跨站方案），files: `.helloagents/modules/runbooks/cookie-and-csrf.md`, `docs/SYSTEM_DESIGN.md`, verify why.md#scenario-samesiteoriging-策略与前端部署形态一致

## 19. R19 API DTO 化与字段暴露控制（避免 entity 直出）
- [√] 19.1 content-service：将评论/回复列表改为返回 DTO 白名单（不暴露治理字段），files: `content-service/src/main/java/com/nowcoder/community/content/api/PostController.java`, `content-service/src/main/java/com/nowcoder/community/content/api/dto/CommentResponse.java`, verify why.md#scenario-公共评论回复接口不暴露治理字段
- [√] 19.2 frontend：适配评论/回复字段映射并补齐契约约束，files: `frontend/src/views/PostDetailView.vue`, `frontend/src/api/services/postService.js`, verify why.md#scenario-接口契约可演进且不绑定表结构
- [√] 19.3 增加契约/回归测试：确保公共接口字段白名单稳定，files: `content-service/src/test/java/com/nowcoder/community/content/api/PostControllerTest.java`, `docs/SYSTEM_DESIGN.md`, verify why.md#scenario-接口契约可演进且不绑定表结构

## 20. R20 common 自动装配化（Boot AutoConfiguration）与一致性
- [√] 20.1 common：新增 Boot AutoConfiguration imports，并注册通用 auto-config（StartupValidation、DLQ Publisher、internal client 规范等），files: `common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, `common/src/main/java/com/nowcoder/community/common/autoconfig/CommonAutoConfiguration.java`, verify why.md#scenario-common-的-cross-cutting-能力在所有服务中一致生效
- [√] 20.2 common：为 servlet/reactive 做条件装配，避免 gateway（reactive）加载 servlet Filter，files: `common/src/main/java/com/nowcoder/community/common/autoconfig/ServletOnlyAutoConfiguration.java`, `common/src/main/java/com/nowcoder/community/common/autoconfig/ReactiveOnlyAutoConfiguration.java`, verify why.md#scenario-servletreactive-条件装配避免“错误类型的-bean-被加载”
- [√] 20.3 gateway：补齐“common 自动装配生效”的启动验证（smoke），files: `gateway/src/test/java/com/nowcoder/community/gateway/StartupSmokeTest.java`, `.helloagents/modules/gateway.md`, verify why.md#scenario-common-的-cross-cutting-能力在所有服务中一致生效

## 21. R21 对象级鉴权（OwnerGuard）与 IDOR 治理
- [√] 21.1 message-service：会话详情接口增加成员校验（conversationId 必须包含当前用户），并在 SQL 层加 owner 条件（from_id/to_id），files: `message-service/src/main/java/com/nowcoder/community/message/api/MessageController.java`, `message-service/src/main/java/com/nowcoder/community/message/service/PrivateMessageService.java`, `message-service/src/main/resources/mapper/message_mapper.xml`, verify why.md#scenario-非会话成员不能读取会话消息（conversationid-越权）
- [√] 21.2 message-service：私信 markRead 增加 to_id=当前用户 的 owner 约束（ids 越权不得更新他人记录），files: `message-service/src/main/java/com/nowcoder/community/message/api/MessageController.java`, `message-service/src/main/java/com/nowcoder/community/message/service/PrivateMessageService.java`, `message-service/src/main/java/com/nowcoder/community/message/dao/MessageMapper.java`, verify why.md#scenario-非接收方不能标记消息通知为已读（ids-越权）
- [√] 21.3 message-service：通知 markRead 增加 to_id=当前用户 且 from_id=SYSTEM 的 owner 约束（ids 越权不得更新他人记录），files: `message-service/src/main/java/com/nowcoder/community/message/api/NoticeController.java`, `message-service/src/main/java/com/nowcoder/community/message/service/NoticeService.java`, `message-service/src/main/java/com/nowcoder/community/message/dao/MessageMapper.java`, verify why.md#scenario-非接收方不能标记消息通知为已读（ids-越权）
- [√] 21.4 common：补齐 OwnerGuard/AccessPolicy 的通用断言与指标（owner mismatch/invalid conversationId），files: `common/src/main/java/com/nowcoder/community/common/security/OwnerGuard.java`, `common/src/main/java/com/nowcoder/community/common/security/ConversationIdParser.java`, verify why.md#scenario-对象级鉴权必须在-db-层可证明（避免仅-controller-校验）
- [√] 21.5 回归测试：非成员访问会话详情应 404/403；越权 ids 不得更新他人记录，files: `message-service/src/test/java/com/nowcoder/community/message/api/MessageControllerSecurityTest.java`, `message-service/src/test/java/com/nowcoder/community/message/api/NoticeControllerSecurityTest.java`, verify why.md#scenario-非会话成员不能读取会话消息（conversationid-越权）

## 22. R22 服务侧 IP 解析一致化（可信代理）
- [√] 22.1 common：下沉 `TrustedProxyProperties` + servlet 侧 `ClientIpResolver`（CIDR 白名单 + XFF 解析规则），files: `common/src/main/java/com/nowcoder/community/common/net/TrustedProxyProperties.java`, `common/src/main/java/com/nowcoder/community/common/net/ClientIpResolver.java`, verify why.md#scenario-伪造-x-forwarded-for-不能绕过登录限流
- [√] 22.2 gateway：将 reactive 侧 `ClientIpResolver` 对齐可信代理边界（仅 remoteAddr ∈ CIDR 才解析 XFF），并补齐 `ip_source` 指标 tag，files: `gateway/src/main/java/com/nowcoder/community/gateway/filter/ClientIpResolver.java`, `gateway/src/main/java/com/nowcoder/community/gateway/config/TrustedProxyProperties.java`, verify why.md#scenario-仅在可信代理链路中信任-xff
- [√] 22.3 auth-service：替换 `AuthService#clientIp` 为统一 resolver（旁路/不可信链路忽略 XFF），并将 `ip_source`（remote/xff）写入登录限流指标，files: `auth-service/src/main/java/com/nowcoder/community/auth/service/AuthService.java`, `auth-service/src/main/java/com/nowcoder/community/auth/service/LoginRateLimitService.java`, verify why.md#scenario-伪造-x-forwarded-for-不能绕过登录限流
- [√] 22.4 deploy：在 prod/staging 显式配置可信代理 CIDRs（gateway/auth-service），files: `deploy/nacos-config/gateway.yaml`, `deploy/nacos-config/auth-service.yaml`, verify why.md#requirement-r22-服务侧-ip-解析一致化（可信代理）

## 23. R23 资源关系校验固化（Path Semantics）
- [√] 23.1 content-service：新增 DAO exists/join 查询用于校验 `commentId` 属于 `postId`（避免 replies 跨帖枚举），files: `content-service/src/main/java/com/nowcoder/community/content/dao/CommentMapper.java`, `content-service/src/main/resources/mapper/comment-mapper.xml`, verify why.md#scenario-replies-接口必须校验-commentid-属于-postid
- [√] 23.2 content-service：在 service/DAO 层提供可复用断言并在 replies 接口使用（不匹配返回 404），files: `content-service/src/main/java/com/nowcoder/community/content/service/CommentService.java`, `content-service/src/main/java/com/nowcoder/community/content/api/PostController.java`, verify why.md#scenario-资源关系校验在-service-dao-层可复用
- [√] 23.3 回归测试：非法父子关系返回 404（默认防枚举），files: `content-service/src/test/java/com/nowcoder/community/content/api/PostControllerPathSemanticsTest.java`, verify why.md#scenario-replies-接口必须校验-commentid-属于-postid

## 24. R24 输入校验与 payload 限额（防 DoS / 数据污染）
- [√] 24.1 common：定义 `ValidationLimits`（文本长度/列表数量 SSOT）并统一校验错误输出为 HTTP 400，files: `common/src/main/java/com/nowcoder/community/common/validation/ValidationLimits.java`, `common/src/main/java/com/nowcoder/community/common/web/GlobalExceptionHandler.java`, verify why.md#scenario-超长文本输入被拒绝（postcommentmessageregistration）
- [√] 24.2 auth-service：为登录/注册相关 DTO 补齐 `@Size(max=...)`（username/password/email 等），files: `auth-service/src/main/java/com/nowcoder/community/auth/api/dto/LoginRequest.java`, `auth-service/src/main/java/com/nowcoder/community/auth/api/dto/RegisterRequest.java`, verify why.md#scenario-超长文本输入被拒绝（postcommentmessageregistration）
- [√] 24.3 content-service：为发帖/编辑帖 DTO 补齐 `@Size(max=...)`（title/content/tags 等），files: `content-service/src/main/java/com/nowcoder/community/content/api/dto/CreatePostRequest.java`, `content-service/src/main/java/com/nowcoder/community/content/api/dto/UpdatePostRequest.java`, verify why.md#scenario-超长文本输入被拒绝（postcommentmessageregistration）
- [√] 24.4 message-service：为私信发送与标记已读请求补齐长度/数量上限（content/ids），files: `message-service/src/main/java/com/nowcoder/community/message/api/dto/SendMessageRequest.java`, `message-service/src/main/java/com/nowcoder/community/message/api/dto/MarkReadRequest.java`, verify why.md#scenario-超长列表嵌套参数被拒绝（idstags-等）
- [√] 24.5 gateway：增加 request body size limit（全局或按路由），并对超限统一返回 HTTP 400（不进入下游），files: `gateway/src/main/java/com/nowcoder/community/gateway/filter/RequestSizeLimitGlobalFilter.java`, `gateway/src/main/resources/application.yml`, verify why.md#requirement-r24-输入校验与-payload-限额（防-dos--数据污染）

## 25. R25 头像上传与更新链路 fail-closed（可验证 + 限额）
- [√] 25.1 frontend：移除“上传失败仍更新头像”的 demo 兜底逻辑（或仅在 dev 显式开关下允许），files: `frontend/src/views/SettingsView.vue`, verify why.md#scenario-上传失败不能仍然更新头像（前端-fail-closed）
- [√] 25.2 user-service：upload-token 签发时绑定 fileName→userId（Redis TTL），updateAvatar 必须匹配并一次性消费（不匹配直接拒绝），files: `user-service/src/main/java/com/nowcoder/community/user/service/AvatarService.java`, `user-service/src/main/java/com/nowcoder/community/user/api/UserController.java`, verify why.md#scenario-头像-filename-必须绑定当前用户且可验证
- [√] 25.3 user-service：为对象存储 upload token 增加大小/类型/key 前缀限制（优先在存储侧拒绝），files: `user-service/src/main/java/com/nowcoder/community/user/service/AvatarService.java`, verify why.md#scenario-上传-token-必须限制大小类型（防滥用）
- [√] 25.4 文档与 SSOT：固化头像上传的限额与安全约束（允许类型/大小/失败语义），files: `docs/SECURITY.md`, `.helloagents/project.md`, verify why.md#requirement-r25-头像上传与更新链路-fail-closed（可验证--限额）

## 26. R26 敏感词过滤资源加载 fail-fast（生产 fail-closed）
- [√] 26.1 content-service：敏感词词典加载失败在非 dev 环境必须 fail-fast，并输出加载词条数量日志/指标，files: `content-service/src/main/java/com/nowcoder/community/content/util/SensitiveFilter.java`, verify why.md#scenario-sensitive-words-资源缺失时-prod-必须-fail-fast
- [√] 26.2 回归测试：词典资源必须可被加载（至少验证“资源存在且能读取”），files: `content-service/src/test/java/com/nowcoder/community/content/util/SensitiveFilterLoadTest.java`, verify why.md#scenario-敏感词词典加载结果可观测

## 27. R27 HTTP 写接口幂等与重复提交治理（Idempotency-Key）
- [√] 27.1 common：下沉 `IdempotencyGuard`（Redis 存储 + 状态机 + 响应缓存），并固化错误语义（并发/重复/存储不可用），files: `common/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyGuard.java`, `common/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyStore.java`, `common/src/main/java/com/nowcoder/community/common/idempotency/RedisIdempotencyStore.java`, `common/pom.xml`, verify why.md#scenario-重复提交发帖评论不会产生重复数据
- [√] 27.2 frontend：对发帖/评论/私信等关键写接口统一生成并发送 `Idempotency-Key`（同一用户重复点击/重试复用同 key），files: `frontend/src/api/http.js`, verify why.md#scenario-重复提交发帖评论不会产生重复数据
- [√] 27.3 content-service：为 `POST /api/posts` 与 `POST /api/posts/*/comments` 接入幂等保护（返回同一 postId/commentId），files: `content-service/src/main/java/com/nowcoder/community/content/api/PostController.java`, verify why.md#scenario-重复提交发帖评论不会产生重复数据
- [√] 27.4 message-service：为 `POST /api/messages` 接入幂等保护（同 key 只写入一次），files: `message-service/src/main/java/com/nowcoder/community/message/api/MessageController.java`, `message-service/pom.xml`, verify why.md#scenario-重复提交私信不会产生重复消息
- [√] 27.5 关键写接口在幂等存储不可用时按 fail-closed 返回 503（仅对“必须幂等”接口生效），files: `common/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyGuard.java`, verify why.md#scenario-幂等存储不可用时关键写接口-fail-closed

## 28. R28 internal 运维/内部写接口防滥用治理（single-flight + 双人确认）
- [√] 28.1 common：识别 OPS 类 internal 路由（reindex/outbox replay/改密码/治理处置等）并增加 `InternalOpsGuardFilter`（ops-token + 频率/并发限制 + 审计），files: `common/src/main/java/com/nowcoder/community/common/internal/InternalOpsGuardFilter.java`, `common/src/main/java/com/nowcoder/community/common/internal/InternalTokenFilter.java`, verify why.md#scenario-outbox-replay-属于高风险操作必须强保护
- [√] 28.2 search-service：reindex 增加 single-flight 锁与 jobId（重复触发返回已有 job/409），files: `search-service/src/main/java/com/nowcoder/community/search/api/InternalSearchController.java`, `search-service/src/main/java/com/nowcoder/community/search/service/ReindexJobService.java`, `search-service/src/main/java/com/nowcoder/community/search/api/dto/ReindexResponse.java`, `deploy/nacos-config/search-service.yaml`, verify why.md#scenario-reindex-必须单飞且可审计
- [√] 28.3 content-service/social-service：为 `/internal/*/outbox/replay` 增加 break-glass 开关与强保护（ops-token/allowlist/限流），files: `common/src/main/java/com/nowcoder/community/common/internal/InternalOpsGuardFilter.java`, `deploy/nacos-config/content-service.yaml`, `deploy/nacos-config/social-service.yaml`, verify why.md#scenario-outbox-replay-属于高风险操作必须强保护
- [√] 28.4 user-service：内部高权限写接口（改密码/治理处置）分域 token（最小权限），files: `common/src/main/java/com/nowcoder/community/common/internal/InternalTokenFilter.java`, `user-service/src/main/java/com/nowcoder/community/user/api/InternalUserController.java`, `auth-service/src/main/java/com/nowcoder/community/auth/config/UserServiceClientProperties.java`, `auth-service/src/main/java/com/nowcoder/community/auth/service/UserServiceInternalClient.java`, `content-service/src/main/java/com/nowcoder/community/content/service/UserModerationClient.java`, `deploy/nacos-config/user-service.yaml`, `deploy/nacos-config/auth-service.yaml`, `deploy/nacos-config/content-service.yaml`, verify why.md#scenario-内部写接口按最小权限分域（避免-token-泄露扩大爆炸半径）
- [√] 28.5 deploy/docs：固化 OPS 操作 runbook（默认关闭→临时开启→执行→回滚→关闭）与可选“双人确认”流程，files: `.helloagents/modules/runbooks/internal-ops.md`, `docs/SECURITY.md`, verify why.md#scenario-reindex-必须单飞且可审计

## 29. Security Check
- [√] 29.1 执行安全检查：fail-closed 默认、XFF 信任边界、internal-token 分域、敏感信息（token/PII）不落日志明文

## 30. Testing
- [√] 30.1 回归测试：错误协议（status/code/traceId）全链路一致，file: `common/src/test/java/com/nowcoder/community/common/web/GlobalExceptionHandlerTest.java`
- [√] 30.2 回归测试：写路径投影校验不依赖下游实时可用（下游故障模拟），file: `content-service/src/test/java/com/nowcoder/community/content/service/CommentServiceTest.java`
- [√] 30.3 回归测试：refresh token store（redis）在重启/多实例下可用，file: `auth-service/src/test/java/com/nowcoder/community/auth/service/RefreshTokenStoreTest.java`
- [√] 30.4 回归测试：聚合接口无 N+1（DB/RPC）并保持降级可观测，file: `message-service/src/test/java/com/nowcoder/community/message/api/MessageControllerTest.java`
- [√] 30.5 回归测试：事件 version/type unknown handling（DLQ/skip）符合约定，file: `common/src/test/java/com/nowcoder/community/common/event/EventEnvelopeParserTest.java`
