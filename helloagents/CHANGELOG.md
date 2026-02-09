# Changelog

本文件记录项目（含知识库/架构/代码）的重要变更。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- build：新增 `user-api`/`social-api`/`content-api`/`analytics-api` 作为跨服务 RPC 契约模块（接口/DTO 独立；provider/consumer 依赖 api）。
- common：新增 Dubbo 调用治理 Filter（traceId/traceparent attachment 透传 + Micrometer 指标：调用次数/时延/outcome）。
- internal：服务间同步调用由 HTTP internal client（RestTemplate/WebClient）迁移为 Dubbo RPC（Zookeeper registry）；对外 `/api/**` 路由保持不变（gateway 仍使用 Nacos discovery/config）。
- deploy：`deploy/docker-compose.yml` 补齐 Dubbo registry 地址注入（`DUBBO_REGISTRY_ADDR=zookeeper://.../dubbo`）与依赖启动顺序（`depends_on: zookeeper`）。
- gateway：新增 `GatewayErrorContractTest`，锁定 401/403/400/429/500 的 **HTTP status + Result** 协议，并校验 `X-Trace-Id/traceparent` 与 `Result.traceId` 一致。
- frontend：PostDetail 点赞 read-your-writes 覆盖（短 TTL），降低“写成功但刷新读到旧投影”的可见不一致；SearchView 增加“搜索索引最终一致”提示以管理预期。
- social-service：新增 internal 聚合只读接口 `/internal/social/read/users/{userId}/profile-stats`（一次返回获赞/关注/粉丝/关注状态），供 user-service 收敛主页 fan-out。
- user-service：用户主页聚合改为单次调用 social profile-stats，并在 `/api/users/{userId}` 响应中增加 `socialDegraded`，区分“真实为 0”与“依赖降级占位”。
- message-service：私信 toName 场景的 username→userId resolve 增加短 TTL + 有界容量缓存，降低同步依赖放大与尾延迟。
- common：`IdempotencyGuard` 支持 processing/success TTL 配置化（`http.idempotency.*`），并优化缺失 `Idempotency-Key` 的错误提示与指引。
- scripts：新增 `scripts/doctor.sh`（部署前配置自检，不输出敏感值）与 `scripts/curl-idempotent-post.sh`（幂等写请求示例）。
- social-service：新增 `LikeRemoved`（取消点赞）事件，并提供 internal likes scan 接口用于下游回填点赞投影。
- content-service：消费 `LikeCreated/LikeRemoved` 维护 Redis `like:entity:*` 投影；新增 internal entity resolve（供社交写路径构造可信 payload）与 internal likes backfill（冷启动/纠偏）。
- user-service：积分消费者支持 `LikeRemoved` 触发积分回退，并对 `user.score` 做非负保护，降低“点赞开关刷分”风险。
- common：ops-guard 新增覆盖 `/internal/*/likes/backfill`，统一 break-glass + allowlist + `X-Ops-Token` 保护策略。
- social-service：新增 internal 社交读取 API（计数/关注状态等），供 user-service 聚合读取，减少 /api + Authorization 透传。
- content-service：新增 outbox 内部运维接口（health/replay）并补齐可观测指标（backlog/failed）。
- 运行手册：新增 internal-token 轮转/回滚 runbook，并补齐本次安全审阅结论。
- common：新增事件治理工具（`EventEnvelopeParser` + `UnknownEventAction`），统一消费端 required fields/version 校验，并提供 unknown type/version 的可配置策略。
- common：新增关键写接口幂等保护（`IdempotencyGuard` + Redis store），并提供“存储不可用时 503 fail-closed”的安全默认态。
- common：新增 internal 运维/高风险入口强保护（`InternalOpsGuardFilter`）：break-glass + allowlist + `X-Ops-Token` + 并发(single-flight)/频率限制（Redis 不可用时 503 fail-closed）。
- frontend：关键写请求自动携带并短窗口复用 `Idempotency-Key`，减少重复提交导致的重复写入风险。
- gateway：新增对外运维入口 `POST /api/ops/search/reindex`（经网关转发到 `/internal/search/reindex`，由下游 internal 保护器执行 break-glass + allowlist + `X-Ops-Token` 校验）。
- gateway/search-service：补齐 reindex 兼容窗口回归测试（`gateway/GatewaySecurityConfigTest`、`search-service/SearchControllerTest`）。
- content-service：补齐帖子/评论公共读接口 DTO 字段白名单回归测试，防止治理字段对外泄露。
- auth-service：新增 `application-dev.yml` 以支持本地/联调冒烟：固定 captcha、可选回传 activation/reset link。
- deploy：新增 `docker-compose.mailhog.yml`，支持本地以 SMTP 路径演练（模拟 prod fail-closed，不依赖回传 link）。
- user-service/gateway：新增本地头像上传与访问链路（multipart 上传 + `/files/**` 静态访问路由），并提供 `user.avatar.*` 配置以支持 local/qiniu 存储切换。
- user-service：新增 batch 用户摘要 API（`POST /api/users/batch-summary`）供 feed 聚合补水，降低 N+1 请求风暴。
- social-service：新增批量点赞计数/状态 API（`GET /api/likes/counts`、`GET /api/likes/statuses`），配合 feed 批量补水渲染。
- social-service：新增 Redis Testcontainers 集成测试 `RedisStorageAtomicityTest`，覆盖 `social.storage=redis` 下 follow/like 原子语义与事件失败回滚。
- frontend：feed 列表改为 batch 拉取用户/点赞元信息，并新增 TTL 缓存（60s）；新增 `/#/ops`（Ops Console）与 `/#/admin/users`（用户管理）页面入口。
- scripts：新增 `bootstrap-admin.sh`（管理员角色初始化/修复）与 `smoke-i1-avatar.sh`（local avatar 上传/读取冒烟）。
- content-service/social-service/user-service：补齐 outbox 的 SENDING lease 回收与 SENT 清理策略单测，确保 H2/MySQL 下行为一致。
- common：新增 `HtmlEntityCodec`（基础 HTML entity 白名单编解码），用于历史内容兼容与避免二次转义可见问题。
- gateway：analytics 采集新增 `AnalyticsCollectDispatcher`（有界队列 + 异步 worker + 指标），采集链路与主转发隔离。
- frontend：`UiToast` 支持可选 action（`actionText/onAction`），用于发帖/编辑成功后提供快捷入口。

### Removed
- internal：移除 internal/ops header token 机制（不再使用 `X-Internal-Token` / `X-Ops-Token`），清理 `*_INTERNAL_TOKEN` / `OPS_*_TOKEN` 配置与前端/脚本输入；internal 访问边界回归为部署/网关（gateway `denyAll /internal/**`）。
- internal client：删除已废弃的 HTTP internal client 基建（各服务 `*RestClientConfig`/`*ClientProperties` 等）并清理 `base-url/connect-timeout/read-timeout` 等配置项，避免双栈漂移。

### Fixed
- social-service：关注/拉黑“自操作”错误收敛为 `SocialErrorCode`（13001/13002），避免以通用 400 掩盖领域语义。
- social-service：修复 `social.storage=redis` 写路径的并发与一致性缺口：follow 改为 Lua 原子双写（`followee:*` + `follower:*`），like 改为 Lua 原子更新（`like:entity:*` + `like:user:*`，含非负保护）；事件入队失败 best-effort 回滚 Redis 状态，降低重复事件与计数漂移风险。
- search-service：reindex single-flight 冲突与存储不可用场景收敛到 `SearchErrorCode`（15003/15002），避免 `SimpleErrorCode(409)` 导致语义丢失。
- content-service：分类/评论/帖子不存在场景收敛到 `ContentErrorCode`（12003/12002/12001），并补齐测试依赖 `spring-security-test` 以支撑契约测试编译。
- runbooks：新增内容渲染迁移与网关采集排障手册（`helloagents/wiki/runbooks/content-rendering-migration.md`、`helloagents/wiki/runbooks/gateway-analytics-collect.md`）。
- user-service：补齐 outbox 运维入口 `/internal/users/outbox/health|replay`，与 content/social 对齐；开发阶段已移除 internal token 鉴权，建议仅在私网调用。
- search-service：reindex single-flight 锁增加续租/心跳（owner=jobId + 原子 renew + owner 校验释放），避免长任务锁过期导致并发重建压垮 ES/下游。
- gateway：为出站 WebClient 增加统一的连接/响应/读写超时与连接池上限（含 pending acquire 限制，配置键 `gateway.webclient.*`），降低极端网络条件下资源耗尽风险。
- docs：新增 `docs/DEV_ONLY.md`，集中说明默认演示账号/固定验证码等 dev-only 便捷能力，并在根 README 做生产禁用提示。
- common：新增 `SingleFlightTaskGuard`（基于 Redis 的分布式 single-flight），用于 @Scheduled cleanup/reconcile 在多实例部署下避免重复执行。
- scripts：新增 `scripts/mysql-migrate-ops-harden-schema.sql`（三库预检 + 去重指导 + 条件 DDL），用于 Outbox/幂等表的唯一约束与关键索引对齐。

### Changed
- common/content-service/message-service：HTTP 写接口幂等新增 DB store（`http.idempotency.enabled/store=DB`），发帖/评论/私信等 required 幂等不再硬依赖 Redis。
- auth-service/user-service：refresh token 存储默认迁移到 DB（user-service 托管 `auth_refresh_token`，仅存 `token_hash`），降低 Redis 抖动放大为“无法登录/无法刷新”的风险。
- gateway/auth-service：Redis 不可用时关键链路降级为 fail-open（网关限流、登录失败计数、验证码存储），避免把 Redis 故障放大为对外 503。
- deploy/mysql-init：对齐 Outbox/幂等表索引形态（`idx_outbox_status_next(status, next_retry_at, id)`、`idx_consumed_event_at(consumed_at, id)`、`idx_search_consumed_at(consumed_at, id)`），并增加 schema drift 自修复以避免历史版本索引缺列导致轮询/清理退化为扫表。
- message-service/search-service：幂等表清理任务改为分批 delete（`order by + limit`）并支持可选 single-flight，降低多实例竞争与下游压力放大风险。
- deploy：修复 `deploy/docker-compose.yml` Tab 缩进，恢复 `docker compose ... config` 校验可用性（`scripts/security-check.sh`）。
- content-service/social-service/user-service：Outbox 默认开启（配置与 properties 默认值对齐），并补强 relay 的 SENDING lease 回收 + SENT 保留期清理（默认关闭）与索引。
- message-service：私信写路径拉黑校验改为“投影优先 + 缺失回源 + 回填”，消除投影缺失/滞后导致的 fail-open 窗口；对外私信接口逐步迁移为 DTO 输出（避免直接暴露实体）。
- social-service：点赞写路径不再信任客户端注入的 `entityUserId/postId`，改为通过 content internal resolve 生成可信 payload 并校验 entity 存在性；follow 写路径收敛仅支持 USER。
- social-service：存储默认值固化为 DB（SSOT），Redis/Memory 仅显式启用；补齐 storage 模式边界说明。
- content-service/social-service/user-service：outbox 的 `deleteSentBefore` SQL 改为 derived table 形式，兼容 H2（单测）与 MySQL（生产）。
- deploy/nacos-config：content/social 默认开启 outbox；internal-token 配置收敛到按服务 token（减少全局兜底）。
- user-service：SocialServiceClient 改为 internal-token 调用 social-service internal read API（移除 Authorization 透传与硬编码 BASE_URL）。
- internal-token：清理各服务 `application.yml` 与 internal client 的 `${...:${INTERNAL_TOKEN:}}` 兜底路径，仅允许按服务 token；同步更新 `scripts/search-reindex.sh` 与 auth-service 文档。
- Kafka 消费端：统一 version/type unknown handling（默认版本不匹配进入 DLQ；未知 type 默认 SKIP 并按 type 去重告警，避免 DLQ 噪音）。
- gateway：显式拒绝 `/internal/**`，并将历史入口 `POST /api/search/internal/reindex` 纳入弃用窗口（`Deprecation: true`，引导迁移到 `/api/ops/**`）。
- gateway：legacy `POST /api/search/internal/reindex` 默认禁用（blocked-path-patterns），并返回 410 迁移提示；保留短期开关用于灰度/回滚。
- content-service/social-service/user-service：outbox 认领支持 `FOR UPDATE SKIP LOCKED`（可配置回退 + 运行期降级），降低多实例 relay 并发时的锁等待与头阻塞风险。
- deploy/mysql-init：补齐 identity（user-service）账号最小权限 grant；compose 透传 `USER_DB_USERNAME/USER_DB_PASSWORD` 供初始化脚本创建账号。
- deploy/nacos-config：content-service datasource 默认指向 `community_content`，并使用 `CONTENT_DB_USERNAME/CONTENT_DB_PASSWORD`（不再复用 `MYSQL_USER/MYSQL_PASSWORD`）。
- auth-service：prod profile 启动校验升级为 fail-closed：禁止回传 activation/reset link、强制 mail.enabled=true 且校验 `spring.mail.host`/`activationBaseUrl` 等关键配置。
- auth-service：找回密码 resetLink 生成逻辑解耦 activationBaseUrl，新增 `auth.password-reset.reset-base-url` 并移除 localhost 隐式回退，避免非本地环境生成错误链接。
- common/auth-service：`StartupValidation`（prod）新增校验 `auth.password-reset.reset-base-url`，避免重置链接基址缺失上线后“接口返回已发送但链接不可用”。
- frontend：注册/找回密码在不回传 link 时给出更清晰提示；reindex UI 对齐后端字段（`indexedCount/jobId`）并支持透传 `X-Ops-Token`。
- user-service：管理员角色变更改为显式 `reason + confirm`，并禁止管理员自降级以避免锁死；设置页头像上传逻辑兼容 local/qiniu 两种 provider。
- content-service：内容渲染契约收敛：写入停止全量 htmlEscape（仅对 `&` 最小化 escape，可配置），读路径对历史 entity 做一次性白名单解码（可配置），并对事件 payload/内部扫描接口输出保持一致。
- gateway：analytics 采集链路重构为“filter 投递 + 异步 worker 调用”，队列满允许丢弃且可观测（不影响主请求转发）。
- frontend：发帖/编辑成功提示补齐“搜索/通知最终一致延迟”，并通过 Toast action 提供“立即查看/去搜索”入口。
- 测试：将部分 Kafka consumer/outbox 测试下沉为纯单元测试（减少 `@SpringBootTest` 占比），并补充测试 Quick win 落地约定（`helloagents/project.md`）。
- 测试：单测环境隔离 Dubbo registry（`dubbo.registry.address: N/A` + `dubbo.consumer.init: false`/`lazy: true`），避免测试强依赖外部 Zookeeper。

### Fixed
- 修复“事件发布默认不可靠（best-effort）导致下游永久不一致”的默认配置问题：写侧入 outbox，relay 重试直至成功或进入 FAILED，可观测且可重放。
- 修复“点赞/热帖分数链路数据源不一致”导致点赞展示与分数计算偏离真实值的问题：用社交事件驱动维护 Redis 点赞投影，并补齐取消点赞事件。
- search-service：修复 `ReindexJobService` 多构造器场景下 Spring 注入失败（`No default constructor`）导致的启动/测试失败问题（通过 `@Autowired` 指定注入构造器）。
- 修复“拉黑关系投影缺失/滞后时 fail-open 放过写操作”的问题：投影缺失时回源 SSOT 并回填。
- 修复 `post:score` 刷新队列 pop 后异常导致 postId 永久丢失的问题：失败回补重试并补齐指标。
- gateway：统计采集去重改为有界 TTL 缓存，并补齐对 analytics 内部调用的 traceId 透传。
- analytics-service：修复 UV/DAU 区间查询 unionKey 未清理导致 Redis key/内存膨胀：区间统计改为临时 key（随机后缀），查询结束 delete + 短 TTL 兜底。
- gateway：修复 analytics 采集手动 subscribe 引入的不可控订阅：将 DAU principal 解析挂载到 reactive 链路并设置短超时，采集失败不影响主链路。
- gateway：修复 traceId 注入重复实现（WebFilter + GlobalFilter）导致维护成本与潜在覆盖困惑：统一为 WebFilter 注入点，并收敛常量引用到 `TraceIdSupport`。
- gateway：修复 OriginGuard 在反代/HTTPS offload 场景下同源判定可能误拦登录/刷新：仅在可信代理 CIDR 命中时解析 `Forwarded/X-Forwarded-*` 恢复公网 scheme/host/port。
- docs：补齐 Redis Key 设计与 internal/ops runbook（路径→header→配置 key 映射 + 403 checklist），降低误配与排障成本。
- frontend：修复 search reindex 进度展示误用 `count` 字段的问题（改用 `indexedCount`）。
- social-service：修复未显式加载 `mapper/*.xml` 导致 outbox mapper 运行时 `BindingException` 的问题，并补齐 `map-underscore-to-camel-case`。
- message-service：修复 `NoticeEventConsumerIntegrationTest` 通过 substring 统计 eventId 导致的偶发失败（traceId 字段可能包含相同子串），改为解析 JSON 精确计数。
- 修复内容渲染二次转义可见问题：历史数据读路径一次性 entity 解码 + 写入停止全量 htmlEscape（仅 `&` 最小化 escape）。
- 修复 trusted-proxy 误配置导致的潜在 XFF 伪造风险：prod profile 下启用启动期 fail-closed 校验（enabled 但 CIDR allowlist 为空/全量信任时阻断启动）。
- 修复 auth-service -> user-service internal client `activate()` 结果返回错误（对齐下游 `result` 字段语义）。
- common：prod 下补齐 internal ops guard 的启动期 fail-closed 校验（enabled 时强制要求 allowlist/ops-token/Redis 配置），并禁止 auth 固定验证码误配上线。

## [0.0.2] - 2026-01-28

### Added
- gateway：补齐 TraceIdWebFilter/TraceIdSupport、可信代理配置与 ClientIpResolver，401/403/429 响应体回填 traceId。
- content-service：评论数原子增量 + 并发回归测试；提供 comment_count 回填脚本。
- search-service：幂等 insert-first + 定时清理；alias/蓝绿 reindex 能力与索引管理器。
- message-service：consumed_event 过期清理任务与索引。
- content-service：Outbox（outbox_event 表 + relay 投递 + 参数开关）。
- internal client：新增 `InternalClientSupport` 并统一 headers/错误映射/指标；客户端增加 fail-open 开关。
- deploy/nacos-config：补齐 trusted-proxy、idempotency、outbox、索引参数等配置。
- Maven 多模块微服务工程：`common`、`gateway`、`auth-service`、`user-service`、`content-service`、`social-service`、`message-service`、`search-service`、`analytics-service`（见根 `pom.xml`）。
- `common` 统一返回 `Result<T>` / 错误码 / 全局异常 / traceId Filter。
- `gateway`：JWT 验签 + 路由 `/api/auth/** -> auth-service` + CORS + traceId 透传。
- `auth-service`：`/api/auth/login|refresh|logout|me`（JWT access + refresh cookie 旋转，Redis 存储）。
- `frontend/`：Vue3 SPA（Vite + Router + Pinia + Axios）用于迭代 0 联调。
- 本地基础设施示例：`deploy/docker-compose.yml`（Nacos/MySQL/Redis）与 `deploy/.env.example`。
- CI 工作流：`.github/workflows/ci.yml`（backend-build/backend-test/frontend-lint-build）。
- 冒烟脚本：`scripts/smoke-i0-auth.sh`（登录→me→refresh→logout）。
- 微服务生产级对齐：`user-service`/`social-service`/`content-service`/`message-service`/`search-service`/`analytics-service` 全链路可运行。
- gateway：审计日志（status/costMs/userId/traceId）+ Redis 多规则限流（输出 `X-RateLimit-*`，含 Prometheus 指标与告警）。
- 压测脚本：`loadtest/k6/community-baseline.js` + `loadtest/README.md`（k6 覆盖登录/发帖/点赞/搜索/私信）。
- 安全检查：`scripts/security-check.sh` + 方案包报告 `helloagents/history/2026-01/202601170935_legacy_cutover_prod_parity/security-check.md`。
- 数据种子：新增 `admin/aaa`（type=1）用于管理/审核与 analytics/reindex 校验。
- 前端 UI 等价骨架：路由分层（/auth/*、/search、/messages/*、/notices/*、/analytics、/settings、/users/*、403/404）与权限 guard（requiresAuth/roles）。
- 前端页面补齐：注册/激活、搜索（含高亮与管理员重建索引确认）、私信会话/详情、通知汇总/详情、统计页、设置页、关注/粉丝列表、403/404。
- 帖子详情交互对齐：评论分页、回复树展开、评论/回复点赞、关注/取关、管理员/版主置顶/加精/删除（二次确认）。
- 前端内部组件库最小集合：Button/Input/Card/Pagination/Empty/ConfirmModal 等，统一交互风格。
- 前端 API service 分层：按域封装 auth/post/social/message/notice/search/analytics/user，支持 Result 解析与 traceId 透传。
- 前端单测补齐：`Result` 解析、`authGuard`、`http` 拦截器与 refresh 逻辑。
- auth-service：找回密码 API（`/api/auth/password/reset/request|confirm`），默认验证码强制；本地/测试可回传 resetLink。
- auth-service：测试分层补齐——新增 `@WebMvcTest` 切片测试（mock 下游依赖）；`@SpringBootTest` 集成测试使用 Testcontainers 启动 Redis，避免 CI 因缺少 Redis 返回 500。
- `common`：新增事务提交后执行工具 `AfterCommitExecutor`（P0 用于消除幽灵事件）。
- `deploy/mysql-init/`：支持 MySQL 同实例多 schema + 最小权限账号（非身份域先拆：content/message/search）。
- Kafka DLQ 运维：新增 `kafka_dlq_published_total` 指标/告警与 `scripts/kafka-replay-dlq.sh` 回放脚本（默认 dry-run + 限量/限速）。
- content-service：新增内部帖子扫描接口 `GET /internal/content/posts`（`X-Internal-Token`），供 search-service 在严格 schema 隔离下完成 reindex 冷启动与修复。
- `deploy/docker-compose.yml` 默认将 Nacos 控制台端口绑定到宿主机 `127.0.0.1:8848`（可用 `NACOS_UI_PORT` 覆盖，仅建议本地/测试使用），便于直接在 UI 上维护配置。
- `common`：新增 internal-token Filter（对 `/internal/**` 强制校验 `X-Internal-Token`）与 Kafka 消费 traceId 注入工具 `KafkaTraceSupport`，并补齐 RestTemplate traceId 透传拦截器。
- `user-service`：新增身份域 internal API（`/internal/users/**`），并支持 legacy（MD5+salt）密码在登录成功后渐进升级为 BCrypt。

### Changed
- search-service 重建索引流程由“删除旧索引”调整为“新索引回填 + alias 切换”。
- 内部调用统一使用 `internal_client_requests_total/internal_client_latency` 指标与 tags。
- 父工程升级到 Spring Boot 3.2.6 + Java 17，并加入 Maven Enforcer 门禁。
- `gateway` 增加 `spring-cloud-starter-loadbalancer` 依赖，修复 `lb://` 路由无法解析实例导致的 503（`Unable to find instance`）。
- `deploy/docker-compose.yml` Kafka 镜像调整为 `confluentinc/cp-kafka` + `cp-zookeeper`（替代不可用的 bitnami/kafka 标签）。
- `deploy/docker-compose.yml` 不再包含 `community-edge`（Nginx），基础 compose 默认不提供“前端统一入口”容器；新增可选端口映射覆盖文件 `deploy/docker-compose.ports.yml`。
- `deploy/docker-compose.yml` 固定 compose project name 为 `community`（避免 Docker Desktop 默认显示为 `deploy` 造成歧义）。
- `deploy/docker-compose.yml` 为 Zookeeper 增加持久化数据卷，避免 Kafka 因 `InconsistentClusterIdException`（clusterId 不一致）启动失败。
- `deploy/docker-compose.yml` 为 Kafka 增加 healthcheck + `restart: on-failure`，并将依赖 Kafka 的服务启动条件统一为等待 `service_healthy`，避免首次启动 Kafka 因 ZK 会话未过期窗口偶发退出导致级联失败。
- 新增“前端直连 gateway”本地部署模式：前端 `12881`（Node + Vite preview，无 Nginx）+ gateway `12882`（见 `deploy/docker-compose.frontend-direct.yml` / `deploy/Dockerfile.frontend`）。
- `gateway` CORS 默认允许 `http://localhost:12881` / `http://localhost:12888`，支持前端跨端口直连。
- `gateway` 增加 OriginGuard：对 `/api/auth/login|refresh|logout` 执行 Origin allowlist 校验（服务端硬拦截），并与 CORS allowlist 复用同一份列表。
- `frontend` Vite dev/preview 端口默认 `12881`，支持通过 env 覆盖；端口变化时只需调整 gateway allowlist，避免多服务配置漂移导致 403。
- `frontend` http client 支持 `VITE_API_BASE_URL`，并在本地 `localhost/127.0.0.1` 场景下默认推导到 `http://<host>:12882`。
- `frontend/src/api/http.js` refresh 失败跳转改为硬跳转（避免 router 循环依赖），并兼容无 `location` 的测试环境。
- `frontend/src/api/services/socialService.js` 缓存命中时返回结构统一为 `{ data, traceId }`，避免调用方分支处理。
- `frontend` UI 全量重构为 Notion 风格桌面工作区：引入 Design Tokens（主题/密度）、AppShell 三栏骨架（Sidebar/Topbar）、全站 views 统一排版与组件风格，并增加全局 Toast。
- `frontend` 工作区体验细节增强：新增右侧上下文面板（可持久化开关）、Topbar 搜索 query 回填联动，以及密度 tokens（card/content padding、字号层级）精细化。
- 验证码流程对齐业界最佳实践：`GET /api/auth/captcha` 由 PNG+cookie 改为 `captchaId + imageBase64`（JSON）；校验支持一次性失效 + 失败次数阈值作废；登录改为风险触发强制验证码，注册/找回密码默认强制。
- 安全检查脚本优化：`scripts/secret-scan.sh` 改为仅扫描 git tracked 文件，避免本地 `deploy/.env`（gitignored）阻断 `scripts/security-check.sh`，同时仍会阻止 `deploy/.env` 被提交。
- Kafka 生产端（content/social）：在事务活跃时改为 After-Commit 发送，避免 DB 回滚但事件已发出。
- Kafka 消费端（message）：幂等与业务写入合并为同事务提交，listener 仅在成功后 ack。
- `auth-service`：移除对 MySQL/MyBatis 的依赖，改为通过 `user-service` internal API 完成登录/刷新状态校验/注册/激活/找回密码等身份域能力。
- `frontend` UI 体验系统化优化：补齐 design tokens（hover/active/topbar bg 等）、新增 `pages.css` 页面通用结构样式、路由 meta 驱动 Topbar 标题/副标题、修复移动端侧边栏抽屉行为、统一 Toast 单入口（inject + window.$toast），并对 posts/search/messages/settings/auth 等核心页面做一致性收敛。
- `frontend` BBS UI 打磨：引入导航配置 SSOT（`router/navigation.js`）并重构 Sidebar/MobileNav；posts 列表增加 FeedToolbar（排序/筛选 chips + URL query 同步）与补水并发治理；帖子详情补齐评论/回复锚点定位、高亮、引用回复与草稿；统一角色徽章（ADMIN/MOD）并补齐关键 icon button 的可访问性属性。
- `frontend` 视觉细节再打磨：工作区左右栏分层（Sidebar/RightPanel 使用 `surface`）、FeedToolbar 工具栏卡片化、`UiEmpty` 空态卡片 + CTA slot、RightPanel 分区卡片化与 hover 细节收敛。
- `frontend` BBS 风格收敛：默认密度改为 `compact`（新用户）；帖子列表与搜索结果改为 topic list（紧凑行 + 分隔线/浅底）；card/button/chips hover 去位移动画，整体更贴近 GitHub Discussions 的专业克制风格。
- `frontend` 更 Discourse 一点：posts 列表升级为四列 topic list（含表头），补齐本地未读提示（基于 `lastActivityTime` + `localStorage`），并在 Activity 列展示最后回复人/时间。
- `frontend` 信息架构增强：posts 筛选新增「未读」（chips + Sidebar 入口），RightPanel 增加“快速筛选/热门标签（关键词）”卡片，继续向 Discourse 的侧栏与过滤体验靠拢。
- `content-service`：`GET /api/posts` 列表返回补齐 `lastReplyUserId/lastReplyTime/lastActivityTime`（包含评论与回复评论），支撑前端 Discourse 风格“活动/未读”。
- taxonomy（分类/标签）全链路落地：content-service 新增 `GET /api/categories`、`GET /api/tags/hot`，发帖支持 `categoryId/tags[]`，列表支持按 `categoryId/tag` 过滤；frontend 发帖/列表/详情展示分类与标签，RightPanel 展示分类与热门标签并跳转过滤；gateway 路由与公共 GET 放行补齐；deploy MySQL schema 增加 `category/tag/post_tag` 与 `discuss_post.category_id`。
- taxonomy 体验补齐：content-service 新增 `GET /api/tags/suggest`（标签自动补全）；search-service ES 文档与搜索 API 补齐 `categoryId/tags[]` 并支持 `categoryId/tag` 过滤；frontend 发帖标签输入升级为 chips + suggest，Posts 列表增加“自上次访问后新增 / 上次看到这里”提示，搜索页增加分类/标签过滤。
- `deploy/README.md` 补充前端样式分层与静态资源/文件访问（头像等）策略说明，便于本地与部署排查。
- `deploy/nacos-config/gateway.yaml` 补齐 gateway CORS allowlist 与 OriginGuard allowlist 示例，支持通过 Nacos UI 管理并覆盖默认配置。
- BBS 核心运营能力（治理 + 内容生命周期 + 收藏订阅 + 成长体系）：
  - 举报与治理闭环：`report/moderation_action` 表 + `/api/reports` + `/api/moderation/**`（网关仅 MOD/ADMIN），并通过 Kafka `community.event.moderation.v1` 投递治理通知。
  - 反骚扰：`social-service` 拉黑 API（`/api/blocks/**`）+ internal 关系查询，`message-service` 私信发送前校验拉黑关系。
  - 内容生命周期：帖子/评论编辑窗口（24h/15min）+ 作者软删（PostDeleted）+ 收藏（post_bookmark）+ 分类订阅与“仅看订阅”（user_subscription_category）。
  - 成长体系：积分流水（user_score_log）+ 用户主页展示积分/等级 + `/api/users/leaderboard` 榜单。
  - 本地 compose 修复：补齐 `user-service` 的 `KAFKA_BOOTSTRAP_SERVERS=kafka:9092`，保证容器内积分消费者可连 Kafka。
- `frontend` 内容优先 UI 全站一致性：提升文本对比度（`--text-3`）与 PageHeader 标题层级；topic list 行补齐 `focus-visible`；`UiMarkdown` 渲染升级为段落/列表/引用语义；认证页（含登录/注册）统一收敛到 AuthShell；消息详情/设置/用户主页/通知汇总等页收敛到 `UiCard + UiPageHeader` 模式。
- `frontend` 视觉精修与 CSS 清理：移除历史未用样式入口 `frontend/src/styles.css`；删除确认无引用的遗留选择器（`.post-body/.comment-body`）；补齐 `.btn.sm` 小尺寸变体并以 design tokens 收敛零散间距（更好适配 density）。

### Fixed
- content-service：修复回复评论“跨帖写入/读侧不可达”问题：回复评论必须属于同帖且仅允许回复帖子一级评论（非法请求返回 404）。
- social-service：补齐点赞/关注在“创建关系”场景的拉黑校验（403），避免拉黑后仍产生互动与通知副作用。
- 修复评论并发写入时 comment_count 丢更新问题。
- 修复 message-service 消费端“自调用导致事务不生效 + 幂等记录先写导致永久丢通知”的高风险路径。
- 修复 content-service/social-service 写路径“事务内直接 send Kafka”导致的幽灵事件风险（After-Commit）。
- 修复同步调用缺少超时/降级导致的级联雪崩风险（user-service -> social-service）。
- 修复 search-service 在 Elasticsearch 读路径因 `Instant createTime` 映射不一致导致“有命中即 500”的问题（改为以 epoch millis 存储/读取）。
- 修复 search-service `/internal/search/reindex` 在多 schema 模式下的重建流程：改为调用 content-service 内部 API 扫描帖子数据，移除对 `community_content.*` 的跨 schema 直读与额外 SELECT 授权依赖。
- 修复 `scripts/smoke-i0-auth.sh` 在 zsh 环境下无法通过 `USERNAME` 覆盖账号、且错误打印 Python 片段导致脚本中断的问题（改用 `SMOKE_USERNAME/SMOKE_PASSWORD` + 直接输出响应）。
- 修复前端移动端（≤768px）侧边栏不可见的问题（抽屉与遮罩行为对齐）。
- 修复 internal API token 配置漂移导致的 403：internal-token 校验统一下沉到 `InternalTokenFilter` 并支持按 `/internal/{segment}` 映射到 `{segment}.internal-token`（不再允许 `INTERNAL_TOKEN` 全局兜底，避免爆炸半径扩大）。
- 修复 content-service Kafka 消费端进程内去重在多实例/重启场景下不可靠的问题：移除 JVM `seenEventIds`，并对齐 DefaultErrorHandler+DLQ 与消费端 traceId 注入。

### Removed
- 移除历史单体切流/回滚相关 docker compose 与脚本（`deploy/docker-compose.cutover.yml`、`scripts/cutover/*`）。
- 移除 `deploy/docker-compose.nacos-ui.yml`（废弃 overlay；Nacos 控制台端口已在 `deploy/docker-compose.yml` 默认绑定到宿主机，避免重复端口映射冲突）。
- 移除历史单体模块源码与 Maven module（微服务终局收敛）。
- 移除 `community-edge`（Nginx）容器与相关配置（`deploy/Dockerfile.edge`、`deploy/edge/*`），本地入口统一采用“前端直连 gateway”模式。
- 移除 API 级自动化回归工程与相关 CI job（Playwright request），仓库默认不再提供端到端自动回归门禁。

## [0.0.1] - 2026-01-16

### Added
- 初始化 HelloAGENTS 知识库骨架（`helloagents/`）。
- 新增并归档“Boot 3 + Java 17 + Vue3 + Nacos 微服务化拆分”方案包：`helloagents/history/2026-01/202601161428_boot3_ms_vue3_nacos/`。
