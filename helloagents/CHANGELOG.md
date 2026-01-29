# Changelog

本文件记录项目（含知识库/架构/代码）的重要变更。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- social-service：新增 internal 社交读取 API（计数/关注状态等），供 user-service 聚合读取，减少 /api + Authorization 透传。
- content-service：新增 outbox 内部运维接口（health/replay）并补齐可观测指标（backlog/failed）。
- 运行手册：新增 internal-token 轮转/回滚 runbook，并补齐本次安全审阅结论。
- common：新增事件治理工具（`EventEnvelopeParser` + `UnknownEventAction`），统一消费端 required fields/version 校验，并提供 unknown type/version 的可配置策略。
- common：新增关键写接口幂等保护（`IdempotencyGuard` + Redis store），并提供“存储不可用时 503 fail-closed”的安全默认态。
- common：新增 internal 运维/高风险入口强保护（`InternalOpsGuardFilter`）：break-glass + allowlist + `X-Ops-Token` + 并发(single-flight)/频率限制（Redis 不可用时 503 fail-closed）。
- frontend：关键写请求自动携带并短窗口复用 `Idempotency-Key`，减少重复提交导致的重复写入风险。

### Changed
- social-service：存储默认值固化为 DB（SSOT），Redis/Memory 仅显式启用；补齐 storage 模式边界说明。
- deploy/nacos-config：content/social 默认开启 outbox；internal-token 配置收敛到按服务 token（减少全局兜底）。
- user-service：SocialServiceClient 改为 internal-token 调用 social-service internal read API（移除 Authorization 透传与硬编码 BASE_URL）。
- internal-token：清理各服务 `application.yml` 与 internal client 的 `${...:${INTERNAL_TOKEN:}}` 兜底路径，仅允许按服务 token；同步更新 `scripts/search-reindex.sh` 与 auth-service 文档。
- Kafka 消费端：统一 version/type unknown handling（默认版本不匹配进入 DLQ；未知 type 默认 SKIP 并按 type 去重告警，避免 DLQ 噪音）。

### Fixed
- gateway：统计采集去重改为有界 TTL 缓存，并补齐对 analytics 内部调用的 traceId 透传。

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
- `deploy/docker-compose.nacos-ui.yml` 调整为历史兼容 no-op（避免重复端口映射冲突）。
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
- 移除历史单体模块源码与 Maven module（微服务终局收敛）。
- 移除 `community-edge`（Nginx）容器与相关配置（`deploy/Dockerfile.edge`、`deploy/edge/*`），本地入口统一采用“前端直连 gateway”模式。
- 移除 API 级自动化回归工程与相关 CI job（Playwright request），仓库默认不再提供端到端自动回归门禁。

## [0.0.1] - 2026-01-16

### Added
- 初始化 HelloAGENTS 知识库骨架（`helloagents/`）。
- 新增并归档“Boot 3 + Java 17 + Vue3 + Nacos 微服务化拆分”方案包：`helloagents/history/2026-01/202601161428_boot3_ms_vue3_nacos/`。
