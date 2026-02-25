# Change Proposal: 产品体验闭环与可运营性提升（Self-host/Compose 友好）

## Requirement Background

当前项目的功能覆盖面已经较完整（发帖/评论/点赞/关注/私信/通知/治理/搜索/统计等），但从“产品可用性与可运营性”角度仍存在几个阻断性问题，导致自托管/联调环境难以形成稳定的端到端闭环：

1. **新用户旅程不闭环**：注册后默认提示“去邮箱激活”，但在 `auth-service` 默认配置下邮件发送关闭且激活链接不回传，用户无法自助完成激活/找回密码。
2. **头像上传强依赖外部对象存储**：`user-service` 当前头像上传逻辑强依赖七牛配置；未配置时会直接失败，且前端设置页缺少“自托管可用”的上传路径与清晰引导。
3. **列表/聚合页面存在请求风暴风险**：帖子列表等页面采用“前端补水”的方式逐条拉取作者/点赞等信息，弱网下体验抖动且对网关/后端造成额外负载。
4. **运维入口对管理员不可用或不可理解**：例如“重建索引”属于高风险运维动作，后端采用 break-glass（默认关闭 + allowlist + ops-token）是正确的安全策略，但前端缺少 token/开关/allowlist 的可用性提示与操作面板，导致“按钮存在但不可用”的产品体验。
5. **角色与权限缺少可运营闭环**：当前系统支持 ADMIN/MODERATOR 等角色能力，但缺少安全的“授予/回收/审计/引导”路径；自托管场景需要可重复、可审计的管理员引导与 bootstrap 机制。

## Product Analysis

### Target Users and Scenarios
- **自托管部署者/管理员**：希望用 Docker Compose 一键拉起，完成基础配置后即可使用与运营。
- **普通社区用户**：期望完成注册→激活→发帖→互动的闭环；弱网下也能稳定使用。
- **版主/运营团队**：需要可用的治理后台、可解释的运维入口、可审计的权限变更。
- **研发/测试**：希望在无外部依赖（SMTP/对象存储/第三方云）的情况下完成联调与回归。

### Value Proposition and Success Metrics
- **Onboarding 闭环率**：注册后 5 分钟内完成激活比例、找回密码成功率。
- **首屏性能**：帖子列表首屏请求数、p95 首次渲染耗时、弱网下的可用性。
- **运维成功率**：管理员重建索引/回放等运维操作的成功率与可解释性（失败可自助定位原因）。
- **可运营性**：角色授予/回收可追溯；治理后台使用门槛降低。

### Humanistic Care
- 防用户枚举（找回密码已采用“邮箱不存在也返回已发送”的策略，应保持）。
- 默认最小权限与 fail-closed（运维入口、内部接口、敏感配置不允许 silent fallback）。
- 审计可追溯（权限变更、运维动作需要可定位到操作者/时间/目标）。

## Change Content
1. Onboarding：在 dev/联调环境提供“无 SMTP 也可用”的激活/重置闭环；生产环境保持邮件发送与不回传链接的安全默认。
2. 媒体能力：为头像上传引入可插拔存储（local filesystem / MinIO / Qiniu），默认对 Compose 自托管友好；并修复前端设置页上传/预览/错误引导。
3. Feed 性能：为 UI 关键聚合读路径提供 batch API（用户摘要/点赞计数/点赞状态），减少 N+1 请求；前端改为 batch 拉取与缓存。
4. 运维可用性：提供 Ops Console（管理员）用于配置提示、token 输入与一键操作；修复 reindex 返回字段展示与可解释性。
5. 角色与权限：提供可重复的 bootstrap 与管理能力（脚本/受控 API/前端入口），并补齐审计与防误操作策略。

## Impact Scope
- **Modules:**
  - `auth-service`（注册/激活/找回密码体验与 dev 默认）
  - `user-service`（头像存储策略、用户批量摘要、角色管理）
  - `social-service`（点赞 batch API）
  - `gateway`（/files 资源路由、ops console 路由策略）
  - `frontend`（注册/找回密码提示、设置页头像上传、帖子列表 batch 补水、ops console、用户管理）
  - `deploy`（compose 可选基础设施：mailhog/minio；env 与文档）
- **APIs (新增/调整，保持向后兼容)：**
  - `POST /api/users/batch-summary`（对外安全摘要）
  - `GET /api/likes/counts`、`GET /api/likes/statuses`（batch）
  - `GET /files/**`（头像等静态资源访问，具体由存储策略决定）
  - `GET/POST /api/ops/*`（管理员运维面板需要的“状态/动作”接口，动作仍落到 internal 并受 break-glass 保护）

## Core Scenarios

### Requirement: R1 - onboarding_flow（注册/激活/找回密码闭环） <a id="requirement-r1-onboarding"></a>

#### Scenario: S1 - dev 无 SMTP 也可完成激活/重置 <a id="scenario-r1-s1-dev-no-smtp"></a>
- 用户注册后能拿到激活链接（或在前端可见的“开发/测试激活链接”）。
- 找回密码请求能拿到 resetLink（仅 dev），并能在前端直接完成重置。

#### Scenario: S2 - prod 默认安全态（邮件发送 + 不回传链接） <a id="scenario-r1-s2-prod-secure-default"></a>
- 生产环境注册/找回密码响应不包含激活/重置链接。
- 邮件发送失败不会泄露敏感信息；可观测与告警可定位。

### Requirement: R2 - media_storage（头像/文件自托管友好） <a id="requirement-r2-media"></a>

#### Scenario: S1 - Compose 默认可用（local 或 minio） <a id="scenario-r2-s1-compose-default"></a>
- 在无外网、无第三方对象存储账号的环境下，用户仍可上传头像并正常展示。
- 头像链接可被前端稳定访问（同源 `/files/**` 或明确的公开 base URL）。

#### Scenario: S2 - 可选启用 Qiniu（兼容现有） <a id="scenario-r2-s2-qiniu-optional"></a>
- 配置七牛后仍支持“直传 + ticket 校验”的安全流程。
- 前端设置页能够正确预览与提示错误（而非静默失败）。

### Requirement: R3 - feed_performance（减少请求风暴，弱网可用） <a id="requirement-r3-feed"></a>

#### Scenario: S1 - 列表页请求数可控 <a id="scenario-r3-s1-request-budget"></a>
- 帖子列表页单次加载的后端请求数从“每条帖子多次补水”收敛到“少量 batch 请求”。
- 首屏渲染优先展示文本内容，补水不影响交互与滚动。

#### Scenario: S2 - 缓存与降级策略明确 <a id="scenario-r3-s2-cache-degrade"></a>
- 用户摘要/点赞计数具备短 TTL 缓存（前端或服务端）。
- 下游失败时 UI 可降级：不阻塞列表展示（显示匿名/0 点赞等）。

### Requirement: R4 - ops_usability（运维入口可用、可解释） <a id="requirement-r4-ops"></a>

#### Scenario: S1 - 管理员可自助完成 reindex <a id="scenario-r4-s1-reindex-console"></a>
- Ops Console 提示当前 break-glass 状态（enabled/allowlist/token）。
- 管理员可输入 `X-Ops-Token` 并触发重建索引；成功后展示 `jobId/indexedCount`。
- 失败时给出“可行动”的提示（例如需要开启开关/配置 allowlist）。

### Requirement: R5 - role_management（角色授予/回收可运营） <a id="requirement-r5-role"></a>

#### Scenario: S1 - 有管理员时的受控管理 <a id="scenario-r5-s1-admin-manage"></a>
- ADMIN 可通过用户管理页授予/回收 MODERATOR（以及必要时的 ADMIN），并记录审计日志。

#### Scenario: S2 - 无管理员时的 bootstrap <a id="scenario-r5-s2-bootstrap"></a>
- 提供可重复执行的脚本或受控 break-glass 方式将指定用户提升为 ADMIN（自托管友好）。

## Risk Assessment
- **风险：dev 回传激活/重置链接被误用于生产**  
  **Mitigation：** 仅在 `dev` profile 默认开启；`prod` profile 强制关闭并由启动期校验 fail-closed。
- **风险：本地文件上传带来路径穿越/恶意文件**  
  **Mitigation：** 严格的 key 生成与白名单；限制 MIME/体积；禁止任意路径；必要时做内容嗅探与安全头。
- **风险：batch API 引入数据泄露**  
  **Mitigation：** 仅返回“公开摘要字段”；对需要登录的数据（点赞状态）强制鉴权；加入速率限制与审计。
- **风险：角色管理误操作扩大权限**  
  **Mitigation：** 二次确认 + 审计日志 + 最小可行角色集合；提供回滚路径（降级/撤销）。 

