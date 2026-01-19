# Changelog

本文件记录项目（含知识库/架构/代码）的重要变更。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
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

### Changed
- 父工程升级到 Spring Boot 3.2.6 + Java 17，并加入 Maven Enforcer 门禁。
- `gateway` 增加 `spring-cloud-starter-loadbalancer` 依赖，修复 `lb://` 路由无法解析实例导致的 503（`Unable to find instance`）。
- `deploy/docker-compose.yml` Kafka 镜像调整为 `confluentinc/cp-kafka` + `cp-zookeeper`（替代不可用的 bitnami/kafka 标签）。
- `deploy/docker-compose.yml` 不再包含 `community-edge`（Nginx），基础 compose 默认不提供“前端统一入口”容器；新增可选端口映射覆盖文件 `deploy/docker-compose.ports.yml`。
- `deploy/docker-compose.yml` 为 Zookeeper 增加持久化数据卷，避免 Kafka 因 `InconsistentClusterIdException`（clusterId 不一致）启动失败。
- 新增“前端直连 gateway”本地部署模式：前端 `12881`（Node + Vite preview，无 Nginx）+ gateway `12882`（见 `deploy/docker-compose.frontend-direct.yml` / `deploy/Dockerfile.frontend`）。
- `gateway` CORS 默认允许 `http://localhost:12881`，支持前端跨端口直连。
- `frontend` Vite dev 端口固定为 `12881`（与 docker preview 对齐），同时 gateway CORS 与 auth-service `Origin` 白名单默认仅允许 `http://localhost:12881`，避免端口漂移导致 403。
- `frontend` http client 支持 `VITE_API_BASE_URL`，并在本地 `localhost/127.0.0.1` 场景下默认推导到 `http://<host>:12882`。
- `frontend/src/api/http.js` refresh 失败跳转改为硬跳转（避免 router 循环依赖），并兼容无 `location` 的测试环境。
- `frontend/src/api/services/socialService.js` 缓存命中时返回结构统一为 `{ data, traceId }`，避免调用方分支处理。
- `frontend` UI 全量重构为 Notion 风格桌面工作区：引入 Design Tokens（主题/密度）、AppShell 三栏骨架（Sidebar/Topbar）、全站 views 统一排版与组件风格，并增加全局 Toast。
- `frontend` 工作区体验细节增强：新增右侧上下文面板（可持久化开关）、Topbar 搜索 query 回填联动，以及密度 tokens（card/content padding、字号层级）精细化。
- 验证码流程对齐业界最佳实践：`GET /api/auth/captcha` 由 PNG+cookie 改为 `captchaId + imageBase64`（JSON）；校验支持一次性失效 + 失败次数阈值作废；登录改为风险触发强制验证码，注册/找回密码默认强制。
- 安全检查脚本优化：`scripts/secret-scan.sh` 改为仅扫描 git tracked 文件，避免本地 `deploy/.env`（gitignored）阻断 `scripts/security-check.sh`，同时仍会阻止 `deploy/.env` 被提交。
- Kafka 生产端（content/social）：在事务活跃时改为 After-Commit 发送，避免 DB 回滚但事件已发出。
- Kafka 消费端（message）：幂等与业务写入合并为同事务提交，listener 仅在成功后 ack。

### Fixed
- 修复 message-service 消费端“自调用导致事务不生效 + 幂等记录先写导致永久丢通知”的高风险路径。
- 修复 content-service/social-service 写路径“事务内直接 send Kafka”导致的幽灵事件风险（After-Commit）。
- 修复同步调用缺少超时/降级导致的级联雪崩风险（user-service -> social-service）。
- 修复 search-service 在 Elasticsearch 读路径因 `Instant createTime` 映射不一致导致“有命中即 500”的问题（改为以 epoch millis 存储/读取）。
- 修复 search-service `/internal/search/reindex` 在多 schema 模式下的重建流程：改为调用 content-service 内部 API 扫描帖子数据，移除对 `community_content.*` 的跨 schema 直读与额外 SELECT 授权依赖。
- 修复 `scripts/smoke-i0-auth.sh` 在 zsh 环境下无法通过 `USERNAME` 覆盖账号、且错误打印 Python 片段导致脚本中断的问题（改用 `SMOKE_USERNAME/SMOKE_PASSWORD` + 直接输出响应）。

### Removed
- 移除历史单体切流/回滚相关 docker compose 与脚本（`deploy/docker-compose.cutover.yml`、`scripts/cutover/*`）。
- 移除历史单体模块源码与 Maven module（微服务终局收敛）。
- 移除 `community-edge`（Nginx）容器与相关配置（`deploy/Dockerfile.edge`、`deploy/edge/*`），本地入口统一采用“前端直连 gateway”模式。
- 移除 API 级自动化回归工程与相关 CI job（Playwright request），仓库默认不再提供端到端自动回归门禁。

## [0.0.1] - 2026-01-16

### Added
- 初始化 HelloAGENTS 知识库骨架（`helloagents/`）。
- 新增并归档“Boot 3 + Java 17 + Vue3 + Nacos 微服务化拆分”方案包：`helloagents/history/2026-01/202601161428_boot3_ms_vue3_nacos/`。
