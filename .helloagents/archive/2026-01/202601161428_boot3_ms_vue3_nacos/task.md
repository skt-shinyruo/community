# 任务列表：Boot 3 + Java 17 + Vue3 + Nacos 微服务化拆分

Directory: `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/`

验收清单（DoD + 用例矩阵）：`.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/acceptance.md`

版本矩阵与依赖升级清单：`.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/version-matrix.md`

多模块改造 Runbook：`.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/multi-module-migration.md`

JWT 策略与权限矩阵：`.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/auth-jwt-strategy.md`

事件契约与幂等策略：`.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/event-contract.md`

CI 与回归入口：`.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/ci-plan.md`

---

## 0. 迭代划分（里程碑）
- 迭代 0：Boot 3/Java 17 + 微服务底座（Gateway + Auth）+ 全局规范 + Vue3 基础工程
- 迭代 1：search/message/analytics 拆分
- 迭代 2：social-service 拆分
- 迭代 3：content-service + user-service 拆分与单体下线

---

## 1. 迭代 0：基础设施与规范（Gateway + Auth + Vue3）
### 1.0 版本矩阵与迁移基线（先把“能跑通”的最小闭环定义清楚）
- [√] 1.1.1 在 `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/version-matrix.md` 固化版本号（Boot/Cloud/Alibaba/Nacos）与选择理由，verify why.md 中「Requirement: 迭代 0 - 基础设施与规范」
- [√] 1.1.2 在 `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/version-matrix.md` 明确中间件影响范围（MySQL/Redis/Kafka/ES）与升级建议，verify 迭代 0 的 PoC 结论可追溯
- [√] 1.1.3 在 `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/version-matrix.md` 输出“依赖升级/替代清单”（fastjson/ElasticsearchTemplate/kaptcha 等），verify 迭代 0 不被旧依赖阻塞
- [√] 1.1.4 执行版本 PoC：以 `legacy-community` 为载体完成“编译 + 启动”验证，并把阻塞项/替代方案回填 `version-matrix.md`，verify PoC 可复现
- [√] 1.2 明确迭代 0 最小可用闭环（MVP）：`Vue3 -> Gateway -> Auth` 登录/刷新/登出 + 受保护接口 401/403，补充到 `.helloagents/api.md`，verify why.md 中「Scenario: Gateway + Auth 登录鉴权闭环」
- [√] 1.3 制定“迁移期能力开关/降级策略”（例如：ES/Quartz/站内信/搜索可先不迁移或临时关闭），以降低 Boot 3 迁移一次性改动面，记录在 `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/how.md` 的 Security and Performance，verify why.md 中「Requirement: 迭代 0 - 基础设施与规范」

### 1.1 Maven 多模块改造（为微服务拆分做铺垫）
- [√] 1.4.1 先完善迁移 Runbook：补齐小步提交计划/每步验证命令/失败回滚方式，verify `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/multi-module-migration.md`
- [√] 1.4.2 根 `pom.xml` 改造为父工程（packaging=pom），先只声明模块（可为空壳），verify `mvn -q -DskipTests package`
- [√] 1.4.3 创建空模块骨架（不搬代码）：`common`、`gateway`、`auth-service`、`legacy-community` 的 `pom.xml` 与目录，verify 全量构建通过
- [√] 1.4.4 添加 Maven 门禁（建议 maven-enforcer）：强制 Java 17 + Maven 版本，避免“本地能跑 CI 失败”，verify 构建报错清晰
- [√] 1.5.1 `legacy-community` 先做空壳可编译：最小依赖 + 启动类可运行（不要求业务可用），verify `mvn -q -pl legacy-community -am test`
- [√] 1.5.2 迁移 `src/main/java/**` → `legacy-community/src/main/java/**`，verify `mvn -q -pl legacy-community -am test`
- [√] 1.5.3 迁移 `src/main/resources/**`（templates/static/mapper/logback），并逐项验证资源可加载（mapper/模板/日志配置），verify `legacy-community` 启动无资源缺失
- [√] 1.5.4 迁移 `src/test/**` → `legacy-community/src/test/**`（可先 `-DskipTests` 通过，再逐步修复），verify 测试归位
- [√] 1.5.5 根目录清理：确认根 `src/` 不再存在，构建产物路径与 wrapper 正常，verify 全量构建通过

### 1.2 Boot 3 + Java 17 升级（先让 legacy-community 在 Boot3 下“能编译能启动”）
- [√] 1.7 升级 `legacy-community/pom.xml` 到 Java 17 + Spring Boot 3.x，并完成基础依赖升级（MyBatis/Redis/Kafka/Thymeleaf/Actuator 等），verify `mvn -q test`
- [√] 1.8 全量 Jakarta 迁移：`javax.* -> jakarta.*`（例如 `@PostConstruct`、Servlet/Validation 等），verify `legacy-community` 启动成功
- [√] 1.9.1 按 Security 6 重写 `SecurityConfig`：使用 `SecurityFilterChain`，verify 编译通过
- [√] 1.9.2 保持原授权规则（user/admin/moderator）与路径保护（`/discuss/**`、`/data/**` 等）等价，verify 401/403 行为正确
- [√] 1.9.3 保持“Ajax vs 页面请求”返回差异（JSON 403 vs redirect），verify 迁移期行为不退化
- [√] 1.10 兼容性收敛：对 Jakarta 不兼容依赖（如验证码库、旧 ES 客户端等）做升级替换或临时隔离（符合 1.3 的降级策略），verify `legacy-community` 编译通过

### 1.3 common 模块（统一 API/错误码/异常/trace）
- [√] 1.11 新建 `common/pom.xml`，定义统一返回模型（`Result<T>`）与错误码（按模块分段），并在 `.helloagents/api.md` 补充约定，verify how.md 中「API Design（目标态约定）」
- [√] 1.12 在 `common` 新增 `@RestControllerAdvice` 全局异常处理（统一 4xx/5xx 输出结构），verify 前端可稳定解析错误码
- [√] 1.13 在 `common` 新增 traceId 工具：约定 header（如 `X-Trace-Id`）+ MDC 注入，verify `.helloagents/project.md` 的「日志与可观测性」章节

### 1.4 Nacos（注册发现 + 配置中心）
- [√] 1.14 增加本地基础设施启动方式（建议 `deploy/docker-compose.yml`）：Nacos（必须）+ MySQL/Redis（Auth MVP 需要），verify why.md 中「Scenario: Gateway + Auth 登录鉴权闭环」
- [√] 1.15 gateway/auth/legacy-community 接入 Nacos Discovery（服务注册可见），新增各自 `application.yml`/`bootstrap.yml`（按实际版本要求），verify Nacos 控制台可见服务实例
- [√] 1.16 将敏感配置迁移到 Nacos Config（DB/Redis/JWT 等），仓库只保留示例与 README，verify 不提交密钥/Token/账号密码
- [√] 1.17 定义多环境隔离策略（namespace/group/profile），并写入 `.helloagents/project.md`，verify dev/test 可并行

### 1.5 gateway 模块（统一入口：CORS/鉴权/trace/错误收敛）
- [√] 1.18 新建 `gateway` 模块（`gateway/pom.xml`、启动类、Nacos 注册），verify 服务可启动并注册
- [√] 1.19 配置路由：`/api/auth/** -> auth-service`（StripPrefix/RewritePath），并预留后续服务路由占位，verify 登录请求可达
- [√] 1.20 配置全局 CORS（Vue3 开发态）与统一错误返回结构（与 `common` 的 Result 对齐），verify 前端无跨域错误
- [√] 1.21 Gateway 生成并透传 traceId（header + MDC），verify 下游服务日志可按 traceId 串联
- [√] 1.22.1 在 `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/auth-jwt-strategy.md` 固化：token claim/TTL/存储策略/CSRF+CORS/权限矩阵，verify 迭代 0 决策可追溯
- [√] 1.22.2 Gateway 实现 JWT 验签与 roles 解析（从 `Authorization`），并统一 401/403 返回结构（与 common 的 Result 对齐），verify why.md 中「Scenario: Gateway + Auth 登录鉴权闭环」
- [√] 1.22.3 Gateway 实现白名单与权限矩阵（至少覆盖 auth 接口与健康检查），verify `acceptance.md` 的 I0-003/I0-006

### 1.6 auth-service 模块（登录/刷新/登出闭环）
- [√] 1.23 新建 `auth-service` 模块（`auth-service/pom.xml`、启动类、Nacos 注册），verify 服务可启动并注册
- [√] 1.24.1 实现 `POST /api/auth/login`：按 `auth-jwt-strategy.md` 约定签发 token，并统一错误码（账号不存在/未激活/密码错误/限流），verify Vue3 可登录
- [√] 1.24.2 login 响应策略：access token 放 response body；refresh token 写 HttpOnly Cookie（含 SameSite/Secure/Path），verify 刷新链路可用
- [√] 1.25.1 密码兼容：支持现有“MD5+salt”存量校验（迁移期），verify 可登录旧数据
- [√] 1.25.2 密码升级策略：新增/更新密码使用 BCrypt；登录成功后可选“渐进 rehash”（需要可识别算法/字段策略），verify 不破坏存量用户
- [√] 1.26.1 实现 `POST /api/auth/refresh`：refresh token 旋转刷新；refresh 状态存 Redis（黑名单或 token family），verify access 过期可续期
- [√] 1.26.2 refresh 安全：校验 Origin/Referer（配合 Gateway CORS），并处理并发刷新（可先禁止并发/或加宽限期），verify 不易被重放
- [√] 1.27.1 实现 `POST /api/auth/logout`：失效 refresh token（Redis 标记）并清理 cookie，verify 登出后不可刷新
- [√] 1.28 提供 `GET /api/auth/me`（返回 userId/authorities/traceId）用于联调，verify 网关鉴权链路可观测

### 1.7 Vue3 前端（最小可用：登录 + 自动刷新 + 路由守卫）
- [√] 1.29 初始化 `frontend/`（Vite + Vue3 + Router + Pinia + Axios），并补充基础目录结构约定，verify 前端可启动
- [√] 1.30 配置 Vite proxy 指向 Gateway（开发态），verify 无跨域问题
- [√] 1.31 实现登录页（表单校验/错误提示）+ token store（Pinia），verify 登录成功后保存 token 并跳转
- [√] 1.32 axios 拦截器：自动注入 Authorization；401 触发 refresh；refresh 失败回登录页，verify 刷新链路
- [√] 1.33 路由守卫：未登录拦截受保护页面，verify 行为符合预期

### 1.8 联调与冒烟（确保可复现）
- [√] 1.34 补充本地启动文档（启动顺序、Nacos 配置项、前端运行方式），verify 新环境可复现
- [√] 1.35 编写冒烟用例：登录 -> 调用受保护 API -> refresh -> logout（可先脚本/手工步骤，后续再自动化），verify why.md 中「Scenario: Gateway + Auth 登录鉴权闭环」

---

## 2. 迭代 1：旁路服务拆分（search/message/analytics）
### 2.0 事件契约与迁移策略（先统一“怎么说话”，再拆服务）
- [√] 2.1.1 在 `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/event-contract.md` 固化 topic 列表与命名规范，verify why.md 中「Requirement: 迭代 1 - 旁路服务拆分（search/message/analytics）」
- [√] 2.1.2 在 `event-contract.md` 固化事件 envelope 字段（eventId/traceId/version…）与版本演进规则（只加不删），verify 可向后兼容
- [√] 2.1.3 在 `event-contract.md` 输出字段级 payload（Post/Comment/Like/Follow）并标注必填/可选，verify message/search 消费不靠“约定俗成”
- [√] 2.1.4 在 `event-contract.md` 固化 partition key/顺序保证最小约定，verify 同帖事件有序性可解释
- [√] 2.2 在 `common` 中落地统一事件模型（Java DTO），并统一 JSON 序列化（Jackson），verify 生产/消费一致
- [√] 2.3 Kafka 序列化选型：迭代 1 先用 JSON（配合字段级契约）；后续可升级 Avro/Protobuf，并记录 ADR，verify `.helloagents/arch.md`
- [√] 2.4 幂等策略落地：消费者侧基于 `eventId` 去重（优先 DB 表 `consumed_event`，或 Redis Set 作为最小实现），verify 重复投递不重复生效
- [√] 2.5 重试与死信：使用 `@RetryableTopic`（或等价方案）+ `<topic>.dlq`，并规定 DLQ payload 必含错误信息，verify 可观测可排障

### 2.1 search-service（索引与查询，事件驱动更新）
- [√] 2.6 新建 `search-service` 模块骨架：Web + Nacos + Kafka + Elasticsearch 依赖，补齐启动与健康检查，verify 服务在 Nacos 可见
- [√] 2.7 定义搜索索引模型与映射策略（字段：title/content/type/status/createTime/score…；高亮字段处理），记录到 `.helloagents/data.md` 的 Elasticsearch 小节
- [√] 2.8 实现搜索 API：`GET /api/search/posts?keyword=...&page=...`（分页/排序/高亮），verify why.md 中「Scenario: 发帖后可被搜索命中并高亮」
- [√] 2.9 实现 Kafka 消费：消费 PostPublished/PostUpdated/PostDeleted 事件更新索引（保存/删除），verify why.md 中「Scenario: 发帖后可被搜索命中并高亮」
- [√] 2.10 增加索引重建能力（迁移期必需）：提供 `POST /internal/search/reindex` 或脚本（从 DB/内容服务拉取），verify 新环境可冷启动完成索引
- [√] 2.11 补充 search-service 集成测试：至少覆盖“写入索引 -> 搜索命中 -> 高亮返回”最小链路（可用 Testcontainers/本地 ES），verify `*/src/test/**`

### 2.2 message-service（系统通知优先，私信可后置）
- [√] 2.12 新建 `message-service` 模块骨架：Web + Nacos + Kafka + MyBatis/MySQL（或 JPA）依赖，verify 服务在 Nacos 可见
- [√] 2.13 迁移/复用 message 表结构（或新建 notice 表），明确“通知 vs 私信”数据模型边界并写入 `.helloagents/data.md`，verify why.md 中「Scenario: 点赞/评论/关注后产生通知」
- [√] 2.14 实现 Kafka 消费：消费 CommentCreated/LikeCreated/FollowCreated 等事件并落库为通知（包含触发者、目标实体、postId 等），verify why.md 中「Scenario: 点赞/评论/关注后产生通知」
- [√] 2.15 实现通知查询 API：通知列表/未读数/标记已读（REST 风格 `/api/notices/**`），verify why.md 中「Scenario: 点赞/评论/关注后产生通知」
- [√] 2.16（可选）实现私信 API：会话列表/详情/发送（`/api/messages/**`），并计划“是否引入 WebSocket/长轮询”作为后续迭代，verify `.helloagents/api.md`
- [√] 2.17 补充 message-service 集成测试：事件消费 -> 通知落库 -> API 查询返回，verify `*/src/test/**`

### 2.3 analytics-service（统计查询 + 采集位置确定）
- [√] 2.18 明确 UV/DAU 采集位置（推荐：Gateway Filter 采集 + Redis 写入；或调用 analytics-service 记录接口），并记录 ADR，verify `.helloagents/arch.md` 的 ADR 表
- [√] 2.19 新建 `analytics-service` 模块骨架：Web + Nacos + Redis 依赖，verify 服务在 Nacos 可见
- [√] 2.20 实现统计查询 API：`GET /api/analytics/uv`、`GET /api/analytics/dau`（start/end），并对参数做校验与限流（避免大范围查询拖垮 Redis），verify why.md 中「Scenario: UV/DAU 可查询」
- [√] 2.21 若采用“服务化采集”：实现 `POST /internal/analytics/uv/record`、`POST /internal/analytics/dau/record` 并由 Gateway 调用，verify why.md 中「Scenario: UV/DAU 可查询」
- [√] 2.22 补充 analytics-service 测试：日期范围计算、HyperLogLog/Bitmap 逻辑正确性（可用嵌入式 Redis 或 Testcontainers），verify `*/src/test/**`

### 2.4 Gateway 路由与前端接入（把流量切到新服务）
- [√] 2.23 Gateway 增加路由：`/api/search/** -> search-service`、`/api/notices/** -> message-service`、`/api/analytics/** -> analytics-service`，verify 迭代 1 API 可达
- [√] 2.24 Gateway 鉴权策略更新：为迭代 1 的受保护 API 增加权限/白名单规则（与 auth-service 的 authority 对齐），verify 401/403 行为一致
- [√] 2.25 Vue3 接入 search/notice/analytics 的最小页面或联调脚本（可先不做完整 UI，先做接口联调面板），verify why.md 中迭代 1 三个 scenario
- [√] 2.26 清理迁移期旧入口：明确旧 `/search`、`/notice`、`/data` 的兼容/下线路由策略并记录，verify `.helloagents/api.md` 的“迁移期参考”

---

## 3. 迭代 2：高频关系域（social-service）
### 3.0 social-service（点赞/关注/粉丝，Redis 优先）
- [√] 3.1 新建 `social-service` 模块骨架：Web + Nacos + Redis + Kafka + Security（资源服务器）依赖，verify 服务在 Nacos 可见
- [√] 3.2 统一社交关系 API 设计（REST）：点赞/取消、状态查询、计数查询、关注/取关、列表分页，补充到 `.helloagents/api.md`，verify why.md 中「Requirement: 迭代 2 - 社交关系域（social-service）」

### 3.1 点赞能力（Like）
- [√] 3.3 实现 `POST /api/likes`（toggle 或显式 like/unlike），并保证幂等性（重复请求结果一致），verify 点赞状态与计数正确
- [√] 3.4 实现点赞查询：`GET /api/likes/status`、`GET /api/likes/count`（按 entityType/entityId），verify 前端可独立查询
- [√] 3.5 实现用户获赞统计：`GET /api/likes/users/{userId}/count`（或由 user-service 聚合），并写入 `.helloagents/data.md` 的 Redis Key 归属说明

### 3.2 关注/粉丝能力（Follow）
- [√] 3.6 实现 `POST /api/follows` 与 `DELETE /api/follows`（关注/取关），并记录关注时间（ZSet score），verify 列表可按时间排序
- [√] 3.7 实现列表 API：`GET /api/follows/{userId}/followees`、`GET /api/follows/{userId}/followers`（分页），verify why.md 中「Requirement: 迭代 2 - 社交关系域（social-service）」
- [√] 3.8 实现计数 API：关注数/粉丝数/是否关注，verify 用户主页可展示数据

### 3.3 事件与联动（通知依赖）
- [√] 3.9 发布 Like/Follow 事件：LikeCreated/LikeRemoved/FollowCreated/FollowRemoved（或只发布 Created 并在消费端判定），verify message-service 可生成通知
  > Note: 迁移期仅发布 LikeCreated/FollowCreated；取消操作（unlike/unfollow）不发布事件，避免通知回滚复杂度。
- [√] 3.10 message-service 增强：消费 social-service 新事件并生成/更新通知（或仅 Created），verify why.md 中「Scenario: 点赞/评论/关注后产生通知」

### 3.4 网关与前端接入
- [√] 3.11 Gateway 增加 `/api/likes/**`、`/api/follows/**` 路由与鉴权规则，verify API 可达
- [√] 3.12 Vue3 接入点赞/关注最小交互（可先做 demo 页或在帖子详情页落地），verify 点赞/关注功能服务化后保持行为一致

### 3.5 测试
- [√] 3.13 social-service 集成测试：Redis 写入/读取、幂等性、分页边界；并覆盖事件发布，verify `*/src/test/**`

---

## 4. 迭代 3：核心域拆分（content-service + user-service）
### 4.0 领域边界与数据归属决策（避免拆成分布式大泥球）
- [√] 4.1 明确 `auth-service` vs `user-service` 职责边界：谁负责注册/用户创建/密码存储策略/权限来源，并记录 ADR，verify why.md 中「Requirement: 迭代 3 - 核心域拆分」
- [√] 4.2 明确“页面聚合”策略：Vue3 直连多服务 vs 提供聚合 API（BFF）；并记录 ADR（建议先直连，后续按痛点引入 BFF），verify `.helloagents/arch.md`
- [√] 4.3 明确数据拆分策略阶段：共享库（表归属）→ 独立库（迁移方案），并更新 `.helloagents/data.md`，verify how.md 中「Data Model（目标态策略）」

### 4.1 user-service（用户资料与头像）
- [√] 4.4 新建 `user-service` 模块骨架：Web + Nacos + MyBatis/MySQL + Security，verify 服务在 Nacos 可见
- [√] 4.5 抽取用户查询 API：`GET /api/users/{userId}`（资料/统计所需字段），verify why.md 中「Scenario: 用户资料/头像独立服务化」
- [√] 4.6 头像上传策略落地：提供“获取七牛上传凭证/回写头像 URL”API（`PUT /api/users/{userId}/avatar`），verify 头像更新可用
- [√] 4.7（迁移期）从 legacy-community 抽取 user 表相关 mapper/entity 到 user-service（保持字段兼容），verify user-service 可独立读写 user 表
- [√] 4.8 user-service 与 social-service 联动：获取关注/粉丝数（直调或前端聚合），并保证接口稳定，verify 用户主页可展示数据
- [√] 4.9 user-service 集成测试：用户查询/头像更新/权限校验，verify `*/src/test/**`

### 4.2 content-service（帖子/评论/热帖）
- [√] 4.10 新建 `content-service` 模块骨架：Web + Nacos + MyBatis/MySQL + Redis + Kafka + Security，verify 服务在 Nacos 可见
- [√] 4.11 抽取帖子列表 API：`GET /api/posts`（支持最新/热帖排序参数），verify why.md 中「Scenario: 发帖/评论/热帖链路保持功能等价」
- [√] 4.12 抽取发帖 API：`POST /api/posts`（敏感词过滤 + XSS 处理），并发布 PostPublished 事件，verify 发帖后可被搜索命中
- [√] 4.13 抽取帖子详情 API：`GET /api/posts/{postId}`（基础字段 + 评论分页入口），verify 帖子详情可展示
- [√] 4.14 抽取评论 API：`POST /api/posts/{postId}/comments` 与 `GET /api/posts/{postId}/comments`（或统一 comments 资源），发布 CommentCreated 事件，verify 通知可生成
- [√] 4.15 迁移敏感词过滤：将 SensitiveFilter 作为 content-service 内部组件或抽到 common，verify 过滤行为等价
- [√] 4.16 热帖分数刷新迁移：将 Quartz/定时任务迁移到 content-service（或改为 @Scheduled），并与 social-service 点赞数联动（调用/缓存/异步），verify 热帖排序正确
- [√] 4.17 content-service 与 search-service 联动：定义 PostUpdated/ScoreUpdated 事件或重用 publish 事件触发索引更新，verify 搜索排序一致
- [√] 4.18 content-service 集成测试：发帖/评论/热帖刷新/事件发布最小链路，verify `*/src/test/**`

### 4.3 网关切流与遗留系统下线（Thymeleaf 逐步退出）
- [√] 4.19 Gateway 路由完善：`/api/users/**`、`/api/posts/**`、`/api/comments/**`（如有）指向新服务，verify 前端全链路可用
- [√] 4.20 Vue3 落地核心页面：帖子列表/帖子详情/评论/点赞/关注/用户主页（按最小可用逐步上线），verify 迭代 3 两个 scenario
- [√] 4.21 legacy-community 进入只读/停写策略（按模块逐步下线写入口），并记录切换顺序与回滚策略，verify `.helloagents/arch.md`
- [√] 4.22 下线旧 Thymeleaf 页面接口：对 `/index`、`/discuss/**`、`/comment/**`、`/user/**` 等做 301/404 或保留兼容期转发策略，verify `.helloagents/api.md` 的迁移期说明

---

## 5. 安全检查
- [√] 5.1 配置安全：检查仓库不存在明文密钥（Nacos/DB/Redis/Kafka/七牛/JWT secret），并补充示例配置与忽略规则，verify `.helloagents/project.md` 的“配置管理”
- [√] 5.2 鉴权安全：JWT 签名算法/过期时间/刷新策略（旋转刷新、黑名单/家族），以及 401/403 语义一致，verify why.md 中登录闭环
- [√] 5.3 CORS/CSRF：在 Gateway 统一 CORS；若 refresh token 走 Cookie，需要评估 CSRF 防护策略（SameSite/CSRF token），verify `.helloagents/api.md`
- [√] 5.4 账号安全：登录接口增加限流与防爆破（按 IP/账号维度），保留验证码能力的迁移计划，verify Gateway 限流策略
- [√] 5.5 输入安全：对发帖/评论等富文本进行 XSS 防护（白名单/转义策略）并覆盖测试，verify content-service 关键接口
- [√] 5.6 权限审计：管理员/版主接口权限边界清晰（删除/置顶/加精/统计/actuator），并在网关与服务双层校验，verify `.helloagents/api.md`
- [√] 5.7 事件安全：Kafka topic 权限与敏感字段脱敏（邮箱/隐私字段不进事件），verify how.md 中 Security and Performance

---

## 6. 文档同步（知识库）
### 6.0 文档策略（SSOT 与代码同步）
- [√] 6.1 每完成一个迭代（0~3）即同步更新：`.helloagents/overview.md`（模块状态）、`.helloagents/arch.md`（架构与 ADR）、`.helloagents/api.md`（接口）、`.helloagents/data.md`（数据归属），verify 知识库与代码一致

### 6.1 架构与 ADR
- [√] 6.2 更新 `.helloagents/arch.md`：补充迭代 0~3 的关键 ADR（版本矩阵、鉴权策略、聚合策略、数据拆分策略），verify ADR 表可追溯
- [√] 6.3 迁移完成后将方案包迁移到 `.helloagents/archive/YYYY-MM/` 并更新 `.helloagents/archive/_index.md`（按规则记录），verify G11 生命周期要求

### 6.2 API 文档
- [√] 6.4 为各服务补齐 API 细节与示例（auth/user/content/social/message/search/analytics），并标注鉴权与错误码，verify `.helloagents/api.md`
- [√] 6.5 记录迁移期兼容策略：旧接口保留/转发/下线时间表，verify `.helloagents/api.md` 的迁移期章节

### 6.3 数据与事件文档
- [√] 6.6 更新 `.helloagents/data.md`：按服务标注表归属与 Redis key 归属，并补齐事件 schema（字段/版本/示例），verify 数据与事件边界清晰
- [√] 6.7 为消息/搜索/统计等旁路服务补充一致性说明（最终一致 + 幂等），verify `.helloagents/arch.md`

---

## 7. 测试
### 7.0 后端测试分层
- [√] 7.1 common 单元测试：Result/错误码/异常处理/traceId 工具，verify `common/src/test/**`
- [√] 7.2 auth-service 集成测试：login/refresh/logout/权限校验，verify `auth-service/src/test/**`
- [√] 7.3 gateway 冒烟：路由转发/鉴权过滤/CORS/traceId 透传，verify `gateway/src/test/**`
- [√] 7.4 search/message/analytics/social/content/user 各自补齐最小集成测试（各迭代完成时逐步补齐），verify `*/src/test/**`

### 7.1 契约与联调测试
- [√] 7.5 定义服务间 API 契约（OpenAPI/Swagger 或契约测试框架），并在 CI 校验破坏性变更，verify `.helloagents/api.md`
- [√] 7.6 Kafka 事件契约测试：生产/消费 schema 兼容性校验（至少 JSON 字段完整性），verify 迭代 1 的事件清单

### 7.2 前端测试与回归
- [√] 7.7 Vue3 单元测试：store/http 拦截器/路由守卫关键逻辑，verify `frontend/**`

### 7.3 CI 与可复现
- [√] 7.9.1 固化 CI 方案与门禁策略（GitHub Actions），verify `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/ci-plan.md`
- [√] 7.9.2 新增 `.github/workflows/ci.yml`：`backend-build`（`mvn -q -DskipTests package`）+ `backend-test`（`mvn -q test`），verify 迭代 0 DoD
- [√] 7.9.3 新增前端 job：`frontend-lint-build`（npm ci/test/build），并固定 Node 版本与缓存策略，verify PR 可回归
- [√] 7.9.5 设置合并门禁 Required checks（与 `acceptance.md` 0.5 对齐），verify 未通过不可合并（需在 GitHub 仓库 Branch protection 中手动配置）
