# Change Proposal: UI 全量等价（信息架构 + 组件库优先）

## Requirement Background

本项目已从 `legacy-community` 拆分为多微服务（`gateway` + `auth-service`/`content-service`/`social-service`/`message-service`/`search-service`/`analytics-service`/`user-service`），并提供统一 `/api/**` 入口。当前后端 API 能力基本齐全，但 Vue3 前端仅实现了少量路由与页面（登录、帖子列表/详情、用户主页、联调面板），无法做到“用户可点可用”的端到端 UI 等价。

本次目标是同时满足：
1. **API 等价（功能可调用）**：legacy 关键对外能力在新体系中均有等价 API（路径可不同但语义一致），并可由自动化门禁覆盖。
2. **UI 等价（用户可点可用）**：新前端提供与 legacy 关键页面等价的用户路径、交互与权限边界；用户无需手工调用 API/联调面板即可完成核心行为。

### SSOT（验收基线）

以 `legacy-community` 的 **Spring MVC Controller 实际路由** 为功能等价基线（模板文件不作为基线）。当前仓库中 legacy 对外入口来自以下 10 个 Controller：
- `legacy-community/src/main/java/com/nowcoder/community/controller/LoginController.java`
- `legacy-community/src/main/java/com/nowcoder/community/controller/HomeController.java`
- `legacy-community/src/main/java/com/nowcoder/community/controller/DiscussPostController.java`
- `legacy-community/src/main/java/com/nowcoder/community/controller/CommentController.java`
- `legacy-community/src/main/java/com/nowcoder/community/controller/LikeController.java`
- `legacy-community/src/main/java/com/nowcoder/community/controller/FollowController.java`
- `legacy-community/src/main/java/com/nowcoder/community/controller/MessageController.java`
- `legacy-community/src/main/java/com/nowcoder/community/controller/DataController.java`
- `legacy-community/src/main/java/com/nowcoder/community/controller/UserController.java`
- `legacy-community/src/main/java/com/nowcoder/community/controller/AlphaController.java`（示例/调试用，非核心业务）

---

## Product Analysis

### Target Users and Scenarios

- **普通用户**
  - 注册/激活/登录/登出
  - 浏览帖子列表（最新/热帖）、查看帖子详情
  - 发帖、评论、回复
  - 点赞（帖子/评论）、关注/取关用户、查看关注/粉丝列表
  - 私信会话与私信详情、按用户名发送私信
  - 收到评论/点赞/关注通知并查看详情
  - 全局搜索帖子

- **管理员/版主（ADMIN/MODERATOR）**
  - 帖子审核：置顶/加精/删除
  - 统计：UV/DAU 查询（受权限保护）
  - 搜索索引重建（仅 ADMIN）

- **研发/运维**
  - 通过自动化门禁验证功能等价（API + UI）
  - 快速定位错误（统一错误展示 + traceId 透传）

### Value Proposition and Success Metrics

- **价值主张**
  - 将“API 可用”升级为“用户可用”，真正完成从 legacy 页面到新体系 SPA 的交付闭环。
  - 通过信息架构与组件库优先的方式，避免页面堆砌导致的交互割裂与返工。

- **成功度量（建议作为门禁）**
  - 功能对齐矩阵：SSOT 范围内的条目 **100% 标记为已完成（UI + API）**。
  - UI 回归：关键用户路径（注册→激活→登录→发帖→评论/回复→点赞/关注→私信→通知→搜索）可通过 UI 自动化或手工用例稳定复现。
  - 权限边界：未授权用户无法触达管理能力（UI 隐藏 + 后端强校验）。
  - 可观测：前端请求异常可展示 `traceId`（若后端返回），方便定位。

### Humanistic Care

- **可用性**：提供明确的加载/失败/空状态；表单校验提示清晰；重要操作二次确认（删除/置顶/加精）。
- **可访问性**：基础组件支持键盘操作与合理的焦点管理（尤其是弹窗与表单）。
- **隐私与安全**：不在前端本地持久化敏感 token；不在 UI 中泄露内部错误栈；避免将敏感信息写入日志。

---

## Change Content

1. **重做信息架构（IA）与全局布局**
   - 定义全局导航（帖子/搜索/消息/通知/统计/个人/管理），并建立统一的页面布局与路由分层。
2. **组件库优先**
   - 在 `frontend/src/components/ui/` 建立内部基础组件（Button/Input/Card/Modal/Pagination/Tabs/Toast 等），统一视觉与交互规范。
3. **补齐与 legacy 等价的页面与交互**
   - Auth：注册/激活结果/验证码接入（登录/注册前校验）
   - Social：关注/粉丝列表页（含用户信息与关注状态）
   - Message：会话列表/私信详情/发送私信/已读与未读
   - Notice：通知汇总/详情/已读与未读
   - Analytics：统计页面（权限控制）
   - Search：搜索页面（高亮、分页、跳转帖子详情）
   - Moderation：置顶/加精/删除的管理入口（权限控制）
   - Posts：补齐帖子详情页的评论/回复/点赞展示与交互（尽量与 legacy 展示一致）
4. **前端鉴权与权限体验**
   - 基于 `/api/auth/me` 的 `authorities` 做页面级权限（隐藏/禁用/提示），但以后端鉴权为最终裁决。
5. **测试与门禁**
   - 在现有自动化回归（API）基础上，增加 UI 回归（Playwright browser）或最小可行的手工验收矩阵固化。

---

## Impact Scope

- **Modules**
  - `frontend`（主要）
  - `gateway`（仅当需要 BFF/聚合接口或兼容路由时）
  - `content-service`/`social-service`/`message-service`（可能需要补齐聚合字段以避免 N+1）

- **Files（预估范围，非穷举）**
  - `frontend/src/router/*`
  - `frontend/src/views/*`（可能迁移到 `pages/` 分层）
  - `frontend/src/components/ui/*`
  - `frontend/src/api/*`
  - 可能新增：UI 自动化测试目录、`.github/workflows/ci.yml`

- **APIs（核心依赖）**
  - Auth：`/api/auth/*`
  - Posts/Comments：`/api/posts/*`
  - Like/Follow：`/api/likes/*`、`/api/follows/*`
  - Message/Notice：`/api/messages/*`、`/api/notices/*`
  - Search：`/api/search/posts`、`/api/search/internal/reindex`（ADMIN）
  - Analytics：`/api/analytics/uv`、`/api/analytics/dau`（ADMIN/MODERATOR）
  - User：`/api/users/*`

- **Data**
  - 目标阶段不涉及 DB Schema 变更；若新增“聚合接口”也应尽量复用现有数据与索引。

---

## UI / API Parity Matrix（SSOT: legacy Controller 路由）

> 说明：
> - legacy 在部署时通常有 `context-path=/community`（edge legacy 模式转发到 `/community/`），下表仅列出路由相对路径。
> - 新体系 UI 路由以 SPA 为准（示例：`/#/posts`），API 统一通过 `gateway` 的 `/api/**` 访问。

| legacy 入口（页面/接口） | legacy 语义 | 新 API（微服务） | 新 UI（Vue3） | 状态（当前） |
|---|---|---|---|---|
| `GET /register` | 注册页 | `POST /api/auth/register`（auth-service） | `/auth/register` | UI 缺失 |
| `POST /register` | 注册提交 | `POST /api/auth/register` | `/auth/register` | UI 缺失 |
| `GET /activation/{userId}/{code}` | 激活 | `GET /api/auth/activation/{userId}/{code}` | `/auth/activation/:userId/:code` | UI 缺失 |
| `GET /kaptcha` | 验证码图片 | `GET /api/auth/captcha` | `/auth/login`（展示验证码） | UI 交互缺失 |
| `POST /login` | 登录（含验证码） | `POST /api/auth/login`（+可选 captcha verify） | `/auth/login` | UI 部分（缺验证码） |
| `GET /logout` | 退出 | `POST /api/auth/logout` | 全局退出按钮 | 已有（但需统一体验） |
| `GET /index` | 首页帖子列表（最新/热帖） | `GET /api/posts?order=latest|hot` | `/posts` | UI 已有（需对齐展示字段） |
| `POST /discuss/add` | 发帖 | `POST /api/posts` | `/posts`（发帖面板） | UI 已有（基础） |
| `GET /discuss/detail/{id}` | 帖子详情（含评论/回复/点赞状态） | `GET /api/posts/{id}` + `GET /api/posts/{id}/comments` + replies | `/posts/:postId` | UI 部分（缺回复/评论点赞等） |
| `POST /comment/add/{postId}` | 评论/回复 | `POST /api/posts/{postId}/comments` | `/posts/:postId` | UI 部分（缺回复/目标用户交互） |
| `POST /like` | 点赞 toggle | `POST /api/likes` | 帖子/评论按钮 | UI 部分（评论点赞缺失） |
| `POST /follow` | 关注 | `POST /api/follows` | 用户页/列表 | UI 部分（列表页缺失） |
| `POST /unfollow` | 取关 | `DELETE /api/follows` | 用户页/列表 | UI 部分（列表页缺失） |
| `GET /followees/{userId}` | 关注列表页 | `GET /api/follows/{userId}/followees` | `/users/:userId/followees` | UI 缺失 |
| `GET /followers/{userId}` | 粉丝列表页 | `GET /api/follows/{userId}/followers` | `/users/:userId/followers` | UI 缺失 |
| `GET /letter/list` | 私信会话列表 | `GET /api/messages/conversations/detail` | `/messages` | UI 缺失 |
| `GET /letter/detail/{conversationId}` | 私信详情 | `GET /api/messages/conversations/{conversationId}` | `/messages/:conversationId` | UI 缺失 |
| `POST /letter/send` | 按用户名发私信 | `POST /api/messages`（toName/toId） | 私信页输入框 | UI 缺失 |
| `GET /notice/list` | 通知汇总 | `GET /api/notices/summary` | `/notices` | UI 缺失 |
| `GET /notice/detail/{topic}` | 通知详情 | `GET /api/notices?topic=...` | `/notices/:topic` | UI 缺失 |
| `GET /data` | 统计页 | `GET /api/analytics/uv` + `/dau` | `/analytics` | UI 缺失 |
| `POST /discuss/top` | 置顶（ADMIN/MODERATOR） | `POST /api/posts/{id}/top` | `/admin` 或帖子详情管理区 | UI 缺失 |
| `POST /discuss/wonderful` | 加精（ADMIN/MODERATOR） | `POST /api/posts/{id}/wonderful` | `/admin` 或帖子详情管理区 | UI 缺失 |
| `POST /discuss/delete` | 删除（ADMIN/MODERATOR） | `POST /api/posts/{id}/delete` | `/admin` 或帖子详情管理区 | UI 缺失 |
| `GET /user/profile/{userId}` | 用户主页 | `GET /api/users/{userId}`（含 like/follow 聚合） | `/users/:userId` | UI 已有（需补齐列表入口） |
| `GET /user/setting` | 头像设置页 | `GET /api/users/{userId}/avatar/upload-token` + `PUT /api/users/{userId}/avatar` | `/settings` | UI 交互不完整 |

---

## Core Scenarios

### Requirement: 信息架构与组件库
**Module:** frontend

#### Scenario: 全局导航与路由分层
- 用户可从顶部/侧边导航进入帖子/搜索/消息/通知/统计/个人主页。
- URL 结构清晰（按功能域分组），并支持 404/无权限页面。

#### Scenario: 统一交互与错误处理
- 表单校验与错误提示风格统一（组件库提供）。
- API 失败可展示可读错误信息，必要时展示 traceId。

### Requirement: 账号体系 UI 等价（注册/激活/验证码/登录/登出）
**Module:** frontend + auth-service

#### Scenario: 注册→激活→登录闭环
- 用户在注册页提交用户名/密码/邮箱，得到明确结果（成功提示/错误提示）。
- 若开启“暴露激活链接”（开发环境），注册结果页可展示 activationLink；生产环境按邮件激活。
- 激活页展示：成功/重复/失败三态（与后端返回码对齐）。

#### Scenario: 登录验证码
- 登录页展示验证码图片与输入框；验证码错误有明确提示且可刷新验证码。
- 兼容策略：支持后端强制验证码（可配置）或 UI 侧强制校验（最小可行）。

### Requirement: 帖子/评论/回复 UI 等价（含点赞）
**Module:** frontend + content-service + social-service + user-service

#### Scenario: 首页帖子列表
- 支持最新/热帖切换。
- 列表项展示作者信息（用户名/头像/时间）、评论数、点赞数（至少做到与 legacy 信息密度接近）。

#### Scenario: 帖子详情（评论 + 回复树）
- 展示帖子内容、点赞数/状态、评论数。
- 评论列表支持分页；评论项可展开回复列表；支持回复评论（entityType=2）。
- 评论/回复支持点赞（entityType=2，entityId=commentId）。

### Requirement: 关注/粉丝 UI 等价
**Module:** frontend + social-service + user-service

#### Scenario: 关注/粉丝列表页
- 列表展示用户摘要信息（头像/用户名/关注时间），并展示“已关注/关注”按钮（hasFollowed）。
- 支持分页。

### Requirement: 私信 UI 等价
**Module:** frontend + message-service + user-service

#### Scenario: 会话列表与私信详情
- 会话列表展示：最后一条消息、会话消息数、未读数、对端用户信息。
- 私信详情支持分页加载、发送私信、已读标记与未读数刷新。

### Requirement: 通知 UI 等价
**Module:** frontend + message-service

#### Scenario: 通知汇总与详情
- 汇总页展示：每个 topic 的最新消息、总数、未读数。
- 详情页展示通知列表，支持标记已读。

### Requirement: 搜索 UI 等价
**Module:** frontend + search-service

#### Scenario: 搜索与高亮
- 支持关键词搜索、分页、展示高亮片段，点击可进入帖子详情。

### Requirement: 统计 UI 等价（权限）
**Module:** frontend + analytics-service

#### Scenario: UV/DAU 查询
- 管理员/版主可选择日期区间查询 UV/DAU。
- 非授权用户不可访问（UI 提示无权限，后端仍为最终裁决）。

### Requirement: 审核管理 UI 等价（权限）
**Module:** frontend + content-service + gateway

#### Scenario: 帖子置顶/加精/删除
- 仅 ADMIN/MODERATOR 可见操作入口。
- 重要操作二次确认，执行后 UI 状态刷新。

---

## Risk Assessment

- **大改动风险**：信息架构与组件库先行会触发前端结构调整，需控制迁移节奏与回归范围。
  - Mitigation：先建立“新布局/新路由骨架”，再逐页迁移；保留旧页面作为过渡入口（feature flag）。
- **权限误开放风险**：管理入口必须前后端双重约束。
  - Mitigation：UI 仅做体验层控制，后端鉴权保持强校验；新增页面默认 requireAuth + role check。
- **性能风险（N+1）**：列表页如果逐条请求用户信息/点赞数可能导致性能劣化。
  - Mitigation：前端缓存 + 批量接口/BFF（必要时补齐聚合 API）。
- **验证码策略不一致**：若仅 UI 侧校验，API 仍可绕过。
  - Mitigation：提供 auth-service 配置开关，允许后端强制验证码校验（推荐）。
