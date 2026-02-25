# Technical Design: 产品体验闭环与可运营性提升（Self-host/Compose 友好）

## Technical Solution

### Core Technologies
- Backend：Spring Boot 3 + Spring Security + Spring Cloud Gateway + Nacos
- Frontend：Vue3 + Vite + Router + Pinia
- Infra：MySQL/Redis/Kafka/Elasticsearch（均可用 Docker Compose）
- 可选基础设施：
  - MailHog（仅 dev：接收测试邮件）
  - MinIO（仅当选择 S3/对象存储方式时）

### 交付目标与落地顺序（建议）

为保证“最小改动即可显著改善产品可用性”，本方案按优先级拆分为 5 个可独立验收的增量：

1. **P0 Onboarding 闭环（R1）**：注册/激活/找回密码在 dev/Compose 环境开箱即用；prod 默认仍安全。
2. **P0 媒体能力自托管（R2）**：头像上传不再强依赖七牛；local filesystem 作为默认 provider（Compose 友好）。
3. **P1 Feed 性能（R3）**：为列表/聚合读提供 batch API，前端补水从 O(n) 收敛到 O(1)。
4. **P1 Ops 可用性（R4）**：管理员可理解并可操作（提示开关/allowlist/token），修复 reindex 展示与交互。
5. **P1 角色与权限可运营（R5）**：提供 bootstrap 与受控管理能力，并形成审计闭环。

> 约束：全流程不依赖 Kubernetes / service mesh；保持微服务边界，不把业务聚合塞进 gateway。

### Implementation Key Points

1. **Onboarding（auth-service）**
   - **明确环境策略（SSOT）**：dev/staging/prod 行为不同，但都保持“可解释 + 可行动”的用户体验。
   - **dev 默认闭环**（推荐）：
     - `auth.registration.expose-activation-link=true`
     - `auth.password-reset.expose-reset-link=true`
     - mail 仍可保持关闭（`auth.registration.mail.enabled=false`），但用户能从前端直接拿到链接完成激活/重置。
   - **staging/演练**（可选）：
     - 保持 `expose-*-link=false`，引入 MailHog 作为测试 mailbox（或真实 SMTP）。
     - 目标：更贴近生产体验，但不影响联调效率。
   - **prod 默认安全态**（必须）：
     - `expose-activation-link=false`、`expose-reset-link=false`（不在 API 回传链接）
     - mail 通道必须可用（SMTP/第三方服务），否则要能通过日志/告警定位，而不是 silent fallback。
   - **前端文案/交互修复**：
     - 注册成功：若 `activationLink` 为空，提示“已发送邮件/请联系管理员”；若存在则引导“点击链接激活”。
     - 找回密码：同理，避免“提示去邮箱但用户拿不到链接”的断裂体验。

2. **媒体/头像能力（user-service + gateway）**
   - **目标**：在“无外网/无第三方账号”的 Compose 环境中，头像上传与展示可用。
   - **存储 provider 抽象（推荐实现）**：
     - `local`（默认推荐）：后端接收 multipart 上传，写入本地文件系统（Compose volume 持久化），通过 `/files/**` 对外读。
     - `qiniu`（兼容）：保留现有“直传 + ticket 校验”模式（client -> Qiniu 上传）。
     - `s3`（可选增强）：对接 MinIO，提供 presign（可后续迭代）。
   - **统一前端契约（建议最小破坏）**：
     - 保留 `GET /api/users/{userId}/avatar/upload-token`（现有入口），但扩展响应字段以支持不同 provider：
       - `provider`: `local|qiniu`
       - `fileName`: 服务端生成的 key（如 `avatar/{userId}/{uuid}`）
       - `uploadToken`/`bucketUrl`（仅 qiniu）
       - `uploadUrl`/`uploadMethod`（仅 local，例如 `POST /api/users/{userId}/avatar/upload`）
       - `maxBytes`/`mimeLimit`（用于前端校验与提示）
     - 保留 `PUT /api/users/{userId}/avatar`（现有入口）：消费 upload ticket 并更新 `headerUrl`。
   - **本地文件读路径（必须补齐）**：
     - user-service：新增 `GET /files/**`（仅允许读取 `avatar/` 前缀，防路径穿越）。
     - gateway：新增 `/files/** -> lb://user-service` 路由，并在 security 中允许匿名 GET（头像需对外可见）。
   - **headerUrl 生成策略（推荐）**：
     - local：`{USER_PUBLIC_BASE_URL}/files/{fileName}`（存绝对 URL，避免前端端口漂移导致 404）
     - qiniu：`{QINIU_BUCKET_HEADER_URL}/{fileName}`（现有行为）
   - **Compose 落地（推荐）**：
     - user-service 挂载 volume：`user_files:/data/files`
     - env：`USER_AVATAR_STORAGE=local`、`USER_FILES_BASE_DIR=/data/files`、`USER_PUBLIC_BASE_URL=http://localhost:12881`

3. **Feed 性能（user-service + social-service + frontend）**
   - **问题定位（当前实现）**：`PostsView` 对每条帖子逐条拉取作者/点赞/状态，导致请求数与列表长度线性增长。
   - **目标请求预算（建议）**：
     - 单次列表加载：`/api/posts` + `POST /api/users/batch-summary` + `GET /api/likes/counts` +（登录态）`GET /api/likes/statuses` ≈ 3~4 个请求
   - **对外新增 batch API（推荐设计）**：
     - user-service：`POST /api/users/batch-summary`
       - 入参：`{ userIds: number[] }`（最多 200，去重）
       - 出参：`[{ id, username, headerUrl, type? }]`（仅公开字段）
     - social-service：
       - `GET /api/likes/counts?entityType=1&entityIds=1,2,3`（匿名可用）
       - `GET /api/likes/statuses?entityType=1&entityIds=1,2,3`（需要登录）
       - 返回 `Map<entityId, value>`，缺失的 entityId 默认 0/false
   - **前端 PostsView 改造（具体做法）**：
     - 列表 API 返回后收集 `userIds/postIds/lastReplyUserIds`，一次性 batch 拉取；
     - 写入轻量缓存（Pinia store + TTL=60s），避免反复进入列表造成重复请求；
     - 补水失败时降级：作者显示 `user#{id}`，点赞默认 0/false，不阻塞交互。

4. **Ops Console（frontend + gateway + search-service）**
   - **修复现有问题**：
     - SearchView 的 toast 使用了错误字段（当前用 `count`，后端为 `indexedCount`）。
   - **让“安全策略”变得“可用”**（推荐最小产品化）：
     - 在 SearchView 的“重建索引”确认弹窗中新增 ops token 输入框；
     - 调用 `/api/ops/search/reindex` 时携带 `X-Ops-Token` header；
     - 对 403/429/409 等常见失败给出可行动提示：
       - break-glass 未开启 → 提示设置 `OPS_SEARCH_REINDEX_ENABLED=true`
       - allowlist 不匹配 → 提示设置 `OPS_SEARCH_REINDEX_ALLOWLIST=...`
       - token 无效 → 提示设置 `OPS_SEARCH_TOKEN=...`
       - 并发中 → 展示 jobId（后端已有）并提示稍后重试
   - **可选增强（更完整的 Ops Console）**：
     - 新增 `#/ops` 管理页（仅 ADMIN）：
       - 本地输入 `X-Ops-Token`（仅内存保存）
       - 展示配置引导（不回显敏感 token 值）
       - 集中收纳高风险动作（reindex/outbox replay 等）
   - **关键注意**：InternalOpsGuard 仍保持 break-glass 默认关闭，UI 只提升可解释性，不降低安全门槛。

5. **角色与权限管理（user-service + frontend + scripts）**
   - 自托管 bootstrap：
     - 提供 bootstrap 脚本（MySQL 直连或 `docker compose exec mysql`）将指定用户提升为 ADMIN：
       - 支持按 username/email 定位
       - 以审计日志输出“谁/何时/提升了谁”（至少写结构化日志）
       - 提供回滚（降级为 USER）
   - 受控管理（有管理员时）：
     - user-service 提供 admin-only API 修改用户 type（ADMIN/MODERATOR/USER）：
       - 仅 ADMIN 可调用
       - 二次确认字段（例如 `confirm=true`）或强制填写 reason（避免误点）
       - 审计日志：actorUserId/targetUserId/from/to/reason/traceId
     - 前端新增“用户管理”页：
       - 搜索用户（id/username/email 可选）
       - 修改角色（下拉 + 二次确认 + 风险提示）
       - 操作成功后刷新用户信息并提示

## Architecture Design

```mermaid
flowchart TD
  A[Frontend] -->|/api/auth/*| G[gateway]
  A -->|/api/posts| G
  A -->|/api/users/batch-summary| G
  A -->|/api/likes/counts & statuses| G
  A -->|/api/ops/* (admin)| G

  G --> AS[auth-service]
  G --> CS[content-service]
  G --> US[user-service]
  G --> SS[social-service]
  G --> SE[search-service]

  A -->|/files/*| G
  G -->|/files/*| US

  subgraph OpsProtect[break-glass & fail-closed]
    SE -->|/internal/search/reindex| Guard[InternalOpsGuardFilter]
  end
```

## Architecture Decision ADR

### ADR-001: dev 环境回传激活/重置链接
**Context:** 自托管/联调环境默认无 SMTP，注册/找回密码不可用。  
**Decision:** 仅在 `dev` profile 默认开启 `exposeActivationLink/exposeResetLink`，生产默认关闭。  
**Rationale:** 提升开箱即用与联调效率，同时不牺牲生产安全默认态。  
**Alternatives:** 仅记录日志（现状） → 拒绝原因：用户侧不可见，闭环断裂。  
**Impact:** 需要启动期校验防止误把 dev 配置带入 prod。

### ADR-002: 头像存储采用“可插拔 provider”，默认 local
**Context:** 七牛依赖外部账号与网络，不适用于纯自托管场景。  
**Decision:** 引入 storage provider；默认 local filesystem（compose volume 持久化），可选 Qiniu/MinIO。  
**Rationale:** 降低自托管门槛，兼容现有实现。  
**Alternatives:** 新建 media-service → 拒绝原因：引入新服务与运维成本过高。  
**Impact:** 需要补齐文件安全校验与资源访问策略。

### ADR-003: Feed 聚合采用“服务提供 batch API”，而非 gateway BFF
**Context:** 前端逐条补水导致请求风暴；但在 gateway 聚合会增加网关业务耦合。  
**Decision:** 由 user-service/social-service 提供对外 batch read API；前端做一次补水即可。  
**Rationale:** 保持边界清晰（各服务对外提供“本域”读能力），减少耦合。  
**Alternatives:** gateway 聚合 → 拒绝原因：长期演进会把业务聚合压到网关，边界失控。  
**Impact:** 需要新增 API 与前端改造；同时注意限流与鉴权。

### ADR-004: 角色管理采用“脚本 bootstrap + 受控 API 管理”
**Context:** 自托管初期可能没有 ADMIN；但开放“无权限提权”会造成高风险。  
**Decision:** 提供脚本用于 bootstrap（需手工执行）；有 ADMIN 后通过受控 API + UI 管理角色。  
**Rationale:** 风险可控、可审计、可回滚。  
**Impact:** 需要明确文档与风险提示；角色变更必须有审计日志。

## API Design

### POST /api/users/batch-summary
- **Request:** `{ "userIds": [1,2,3] }`
- **Response:** `[{ "id":1, "username":"...", "headerUrl":"..." }]`
- **Notes:** 仅公开字段；限制最大长度（建议 <=200）；返回顺序不保证（前端按 id 组装 map）。

### GET /api/likes/counts
- **Request:** `entityType=1&entityIds=1,2,3`
- **Response:** `{ "1": 10, "2": 0, "3": 7 }`（缺失视为 0）

### GET /api/likes/statuses
- **Request:** `entityType=1&entityIds=1,2,3`（需要登录）
- **Response:** `{ "1": true, "2": false, "3": true }`（缺失视为 false）

### GET /files/*
- **Notes:** 仅服务于公开资源（头像）；仅允许 `avatar/` 前缀；key 必须为服务生成；禁止 `..`、绝对路径与 URL 编码绕过。

## Data Model
- 方案优先不引入新的数据库表（降低迁移风险）；审计日志先写入结构化日志。
- 若后续需要强审计，可新增 `admin_audit_log` 表（本期作为可选增强，不强制）。

## Security and Performance
- **Security**
  - dev-only 行为（激活/重置链接回传、测试 mailbox 等）必须通过 profile 严格隔离；生产 fail-closed。
  - 文件上传：限制 MIME/大小；key 只能由服务签发；禁止用户自定义路径；必要时做内容嗅探。
  - batch API：限流；鉴权（status 需要登录）；避免泄露敏感字段（email/状态等）。
  - 角色变更：二次确认 + 审计日志 + 回滚（降级）路径。
  - 运维入口：保持 break-glass（开关 + allowlist + ops-token + 单飞/限流），并提升 UI 可解释性。
- **Performance**
  - 前端请求收敛：列表补水从 O(n) RPC 收敛到 O(1) batch。
  - 缓存：前端短 TTL；必要时服务端也可加 cache（后续迭代）。

## Testing and Deployment
- **Testing**
  - auth：注册/找回密码在 dev profile 下回传链接、prod 下不回传。
  - user/social：batch API 入参校验、鉴权、限流与正确性。
  - frontend：PostsView 不再逐条调用 profile/like API；SearchView reindex 展示 `indexedCount`。
  - media：local 上传/访问路径与安全校验（拒绝非法 key）。
- **Deployment**
  - Docker Compose：提供可选 `docker-compose.mailhog.yml`、`docker-compose.minio.yml`（若选择）。
  - 默认仍可只启动核心依赖；自托管用户按需打开可选组件。

## 配置清单（建议补齐到 deploy/.env.example）

### auth-service（dev）
- `AUTH_EXPOSE_ACTIVATION_LINK=true`
- `AUTH_EXPOSE_RESET_LINK=true`
- `AUTH_MAIL_ENABLED=false`（可选：true + MailHog/SMTP）

### user-service（avatar/local）
- `USER_AVATAR_STORAGE=local`
- `USER_FILES_BASE_DIR=/data/files`
- `USER_PUBLIC_BASE_URL=http://localhost:12881`

### search-service（ops 可用性）
- `OPS_SEARCH_REINDEX_ENABLED=false`（默认保持关闭）
- `OPS_SEARCH_TOKEN=...`（需要时设置）
- `OPS_SEARCH_REINDEX_ALLOWLIST=...`（建议为堡垒机/内网网段）
- `gateway.trusted-proxy.enabled=true`（使 allowlist 能基于 XFF 生效；生产请配置真实 LB CIDR）
