# 验收清单（DoD + 用例矩阵）：Boot 3 + Java 17 + Vue3 + Nacos 微服务化拆分

Directory: `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/`

> 目的：把“功能完善”转化为可验收、可回归、可追溯的标准。  
> 说明：本清单面向迁移与拆分过程，强调“分迭代闭环 + 门禁（gating）”。每个迭代未达标不得进入下一迭代/切流。

---

## 0. 通用 Definition of Done（所有迭代通用门禁）

### 0.1 工程与交付
- ✅ 所有模块可编译构建通过：`mvn -q -DskipTests package`（或 CI 等价命令）
- ✅ 关键模块可运行（至少能启动并通过健康检查）：gateway/auth（迭代 0），后续迭代增加对应服务
- ✅ 配置可复现：提供本地启动方式（docker compose 或等价），新环境按文档可一键启动

### 0.2 可观测性（Observability）
- ✅ Gateway 生成并透传 `traceId`（例如 `X-Trace-Id`），下游服务日志可按 traceId 串联
- ✅ 关键错误可定位：401/403/500 等响应结构一致，服务端日志含关键信息但不泄露敏感数据

### 0.3 安全与合规
- ✅ 仓库不包含明文密钥（DB/Redis/Kafka/Nacos/七牛/JWT secret 等）
- ✅ 鉴权一致：缺 token → 401；权限不足 → 403；业务异常 → 4xx/5xx 统一结构
- ✅ CORS 策略清晰（开发态与生产态可区分）

### 0.4 文档与契约（SSOT）
- ✅ `.helloagents/api.md`、`.helloagents/arch.md`、`.helloagents/data.md` 与实现一致
- ✅ 事件/接口变更必须同步更新契约与版本策略（向后兼容或明确破坏性变更）

### 0.5 CI 门禁（可执行）
- ✅ CI 方案：默认采用 GitHub Actions；详细见 `.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/ci-plan.md`
- ✅ 工作流文件位置（建议）：`.github/workflows/ci.yml`
- ✅ Required checks（建议从迭代 0 起强制）：`backend-build`、`backend-test`、`frontend-lint-build`

### 0.6 版本基线（必须可追溯）
- ✅ 版本矩阵必须落到文件并可回填：`.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/version-matrix.md`
- ✅ 每次“升版本/换依赖/换中间件”都要更新版本矩阵并补充 PoC 结论（避免靠记忆/口口相传）

---

## 1. 迭代 0 验收（Gateway + Auth + Vue3 + Boot3 迁移底座）

### 1.1 迭代 0 DoD（验收标准）

#### A. 平台升级（Boot 3 / Java 17）
- ✅ `legacy-community`（迁移期单体模块）在 Boot 3 + Java 17 下可编译、可启动（允许通过“能力开关/降级策略”临时关闭部分组件）
- ✅ 完成 Jakarta 迁移（`javax.* -> jakarta.*`），并且不再依赖已废弃的 Spring Security 配置方式

#### B. 微服务底座（Gateway + Nacos）
- ✅ Nacos 本地可用；gateway/auth/legacy-community 能注册到 Nacos 并可见
- ✅ Gateway 能正确转发 `/api/auth/**` 到 auth-service
- ✅ Gateway 具备统一 CORS、统一错误输出、traceId 透传能力

#### C. 鉴权闭环（Auth）
- ✅ 登录：`POST /api/auth/login` 返回 access/refresh（或等价机制）
- ✅ 刷新：`POST /api/auth/refresh` 可在 access 过期后续期
- ✅ 登出：`POST /api/auth/logout` 后 refresh 失效
- ✅ 受保护资源：缺 token 返回 401；权限不足返回 403（至少提供一个 demo protected API 用于联调）

#### D. 前端联调（Vue3）
- ✅ Vue3 能通过 Gateway 完成登录/自动注入 Authorization/401 触发 refresh/refresh 失败回登录页
- ✅ Vue3 开发态无跨域问题（Vite proxy 或 Gateway CORS）

#### E. 回归入口（脚本/命令）
- ✅ 文档给出可复现命令入口（示例，最终以实现为准）：
  - 后端：`mvn -q -DskipTests package`
  - 网关/认证：`mvn -pl gateway -am test`、`mvn -pl auth-service -am test`
  - 前端：`npm -C frontend install`、`npm -C frontend run dev`
  - 冒烟脚本：`scripts/smoke-i0-auth.sh`（需本地已启动 gateway/auth）
  - 本地启动说明：`.helloagents/archive/2026-01/202601161428_boot3_ms_vue3_nacos/run-local.md`

---

### 1.2 迭代 0 用例矩阵（最小闭环）

| Case ID | 场景 | 前置条件 | 步骤（概要） | 期望结果 | 自动化级别 | 回归入口（建议） |
|---------|------|----------|--------------|----------|------------|------------------|
| I0-001 | 登录成功 | 用户存在/密码正确 | Vue3 登录提交 | 返回 token；跳转成功 | 手工 | 手工验收 |
| I0-002 | 登录失败（错误密码） | 用户存在 | 提交错误密码 | 返回业务错误码；前端提示 | 单测/手工 | `auth-service` 测试 |
| I0-003 | 缺失 token 访问保护接口 | 无 token | GET 受保护 API | 401 + 统一结构 | 集成 | `gateway` 测试 |
| I0-004 | token 过期自动刷新 | access 过期/refresh 有效 | 调用受保护 API | 自动 refresh 并重试成功 | 手工 | 手工验收 |
| I0-005 | refresh 失效回登录 | refresh 无效 | 调用受保护 API | refresh 失败，跳转登录 | 手工 | 手工验收 |
| I0-006 | CORS 预检 | 浏览器环境 | OPTIONS 请求 | 预检通过 | 集成 | `gateway` 测试 |
| I0-007 | traceId 贯穿 | 正常请求 | 访问任一 API | 响应/日志可定位 traceId | 手工/集成 | 日志检查/测试 |
| I0-008 | Nacos 注册可见 | Nacos 启动 | 启动服务 | 控制台可见实例 | 手工 | 启动脚本 |

---

## 2. 迭代 1 验收（search/message/analytics 旁路拆分）

### 2.1 迭代 1 DoD（验收标准）

#### A. 事件契约与幂等
- ✅ 事件 envelope 固化并版本化（至少 JSON 字段完整且可向后兼容）
- ✅ 消费端具备幂等去重（基于 eventId 或等价策略）

#### B. search-service
- ✅ 消费 PostPublished/PostDeleted（或等价）事件更新 ES
- ✅ `/api/search/posts` 可分页查询并返回高亮字段
- ✅ 具备索引重建能力（新环境冷启动可完成）

#### C. message-service
- ✅ 消费 comment/like/follow 事件并生成通知
- ✅ 通知列表/未读数/标记已读 API 可用

#### D. analytics-service
- ✅ UV/DAU 可查询；采集位置明确（gateway filter 或服务化采集）
- ✅ 大范围查询具备防护（参数校验/限流/边界限制）

#### E. 网关与前端
- ✅ Gateway 路由到 search/message/analytics
- ✅ Vue3 至少具备联调入口（可为 debug 页或最小页面）

---

### 2.2 迭代 1 用例矩阵（旁路一致性）

| Case ID | 场景 | 前置条件 | 步骤（概要） | 期望结果 | 自动化级别 | 回归入口（建议） |
|---------|------|----------|--------------|----------|------------|------------------|
| I1-001 | 发帖后可搜索命中 | 发布事件可达 | 发帖 -> 搜索 | 结果命中且高亮 | 集成 | content+search 联调 |
| I1-002 | 删帖后索引移除 | delete 事件 | 删帖 -> 搜索 | 结果不再出现 | 集成 | search 测试 |
| I1-003 | 点赞产生通知 | like 事件 | 点赞 -> 查通知 | 通知生成，字段完整 | 集成 | message 测试 |
| I1-004 | 评论产生通知 | comment 事件 | 评论 -> 查通知 | 通知生成 | 集成 | message 测试 |
| I1-005 | UV/DAU 查询 | 采集开启 | 访问/登录 -> 查询 | 统计结果符合预期 | 集成 | analytics 测试 |

---

## 3. 迭代 2 验收（social-service 点赞/关注服务化）

### 3.1 迭代 2 DoD（验收标准）
- ✅ 点赞/取消点赞接口幂等，计数与状态查询正确
- ✅ 关注/取关接口幂等，列表分页与时间排序正确
- ✅ 发布 Like/Follow 事件（至少 Created），message-service 可生成通知
- ✅ Gateway 路由与鉴权规则更新完成；Vue3 可完成最小交互

### 3.2 迭代 2 用例矩阵

| Case ID | 场景 | 前置条件 | 步骤（概要） | 期望结果 | 自动化级别 | 回归入口（建议） |
|---------|------|----------|--------------|----------|------------|------------------|
| I2-001 | 点赞幂等 | 已登录 | 连续点赞/取消 | 状态一致、计数正确 | 集成 | social 测试 |
| I2-002 | 关注列表分页 | 用户有关注 | 查询 followees | 分页正确、按时间排序 | 集成 | social 测试 |
| I2-003 | 关注通知 | message 可消费 | 关注 -> 查通知 | 通知生成 | 集成 | message 测试 |

---

## 4. 迭代 3 验收（content/user 核心域拆分与单体下线）

### 4.1 迭代 3 DoD（验收标准）

#### A. user-service
- ✅ 用户资料 API 可用；头像更新链路可用（七牛签发/回写）
- ✅ 用户主页所需统计（获赞/关注/粉丝）能通过前端聚合或稳定 API 获取

#### B. content-service
- ✅ 帖子列表/详情/评论 API 可用，敏感词过滤与 XSS 防护行为与预期一致
- ✅ 热帖分数刷新可用，并与 search-service 同步（事件或重建）

#### C. 系统整体
- ✅ Vue3 完成核心页面最小集（帖子列表/详情/评论/点赞/关注/用户主页）
- ✅ legacy-community 下线：写入口关闭并有明确回滚策略；旧 Thymeleaf 路由处理完成

---

### 4.2 迭代 3 用例矩阵（端到端）

| Case ID | 场景 | 前置条件 | 步骤（概要） | 期望结果 | 自动化级别 | 回归入口（建议） |
|---------|------|----------|--------------|----------|------------|------------------|
| I3-001 | 端到端：发帖->搜索 | 全链路可用 | 登录->发帖->搜索 | 搜索命中且排序正确 | 手工 | 手工验收 |
| I3-002 | 端到端：评论->通知 | message 可用 | 评论->查通知 | 通知生成且可已读 | 集成/手工 | 手工验收 + message 联调 |
| I3-003 | 用户主页数据 | social/user 可用 | 关注/点赞后访问主页 | 统计数据正确 | 手工 | 手工验收 |
| I3-004 | 旧接口下线 | 切流完成 | 访问旧 URL | 301/404/转发符合策略 | 手工/集成 | gateway 测试 |

---

## 5. 回归脚本入口（约定占位）

> 说明：以下为“应提供”的入口约定，具体命令可在实现阶段落实到 `Makefile`/`scripts/`/CI 中。

- 后端全量构建：`mvn -q -DskipTests package`
- 模块测试（示例）：`mvn -pl gateway -am test` / `mvn -pl auth-service -am test`
- 前端开发：`npm -C frontend run dev`
- 前端构建：`npm -C frontend run build`
