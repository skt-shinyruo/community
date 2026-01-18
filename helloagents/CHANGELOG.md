# Changelog

本文件记录项目（含知识库/架构/代码）的重要变更。
格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- Maven 多模块工程：`legacy-community`、`common`、`gateway`、`auth-service`。
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

### Changed
- 父工程升级到 Spring Boot 3.2.6 + Java 17，并加入 Maven Enforcer 门禁。
- `legacy-community` 完成 Jakarta 迁移与 Security 6 适配，迁移期默认不自动启动 Kafka Listener。
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

### Removed
- `legacy-community` 中的 Elasticsearch 旧实现（迁移期降级，后续迭代 1 由 `search-service` 重写）。
- 移除 legacy 切流/回滚相关 docker compose 与脚本（`deploy/docker-compose.cutover.yml`、`scripts/cutover/*`）；legacy-community 仅保留源码对照。
- 移除 `community-edge`（Nginx）容器与相关配置（`deploy/Dockerfile.edge`、`deploy/edge/*`），本地入口统一采用“前端直连 gateway”模式。
- 移除 API 级自动化回归工程与相关 CI job（Playwright request），仓库默认不再提供端到端自动回归门禁。

## [0.0.1] - 2026-01-16

### Added
- 初始化 HelloAGENTS 知识库骨架（`helloagents/`）。
- 新增并归档“Boot 3 + Java 17 + Vue3 + Nacos 微服务化拆分”方案包：`helloagents/history/2026-01/202601161428_boot3_ms_vue3_nacos/`。
