# IM 私信治理收敛与协议对齐（设计稿）

日期：2026-03-14  
主题：私信/IM 功能割裂治理 + 数据完整性修复 + 统一 `Result<T>` 协议

## 1. 背景与问题

当前仓库存在“私信/IM 功能割裂”与“治理规则旁路”的组合问题：

- 前端 `/messages` 路由实际走 IM：
  - HTTP：`/api/im/**`（im-core）
  - WebSocket：`/ws/im`（im-realtime）
- community-bootstrap 仍保留完整的“站内私信”HTTP API：`/api/messages/**`，并在写路径显式做了治理校验（禁言/拉黑/目标用户存在性）。
- IM 私信写入链路（WS -> Kafka -> im-core 持久化）缺少治理校验，存在绕过社区治理规则的风险。
- im-core 会话 read 接口仅用字符串解析判断成员，且在会话不存在时仍可能 upsert read_state，造成脏数据。
- im-core HTTP 返回体/错误体与主站 `Result<T>` 体系不一致，导致前端需要维护两套错误处理（`http` vs `imCoreHttp`）。

## 2. 目标与非目标

### 2.1 目标

1) IM 私信发送补齐治理约束：禁言/封禁、双向拉黑、目标用户存在。  
2) 修复 im-core `markRead` 的“伪造 conversationId 导致 read_state 脏写入”。  
3) im-core `/api/im/**` 对齐主站：统一 `Result<T>` 响应体 + 401/403/参数错误也返回 `Result`。  
4) 前端不再需要针对 im-core 的“裸 JSON / 非统一错误体”做额外适配，尽量复用 `unwrapResultBody`。  

### 2.2 非目标（本轮不做）

- 不删除/迁移 legacy “站内私信” `/api/messages/**`（仅保证不再误导并保持兼容）。
- 不引入服务间共享密钥（`X-Internal-Token` / mTLS 等）。本方案采用“转发用户 JWT”进行跨服务调用。
- 不把用户 JWT 或其等价证明放进 Kafka command 事件（避免扩大敏感面与成本）。

### 2.3 协议约定（`Result<T>` / trace / WS）

#### 2.3.1 `Result<T>` 统一 envelope（JSON Schema 约定）

本项目（community-bootstrap 与 im-core）统一使用如下 envelope（字段名与类型为约定）：

- `code: number`：业务码（通用错误码优先复用 `CommonErrorCode`，如 400/401/403/404/500/503；成功为 0）
- `message: string`：对用户可读的错误语义（成功可为 `"OK"`）
- `httpStatus: number`：HTTP status 语义提示（通常与 `code` 一致；但客户端不强依赖）
- `data?: T`：成功时返回的数据（允许为 `null`）
- `traceId: string`：链路追踪 ID（32 个 hex 字符，见 2.3.2）
- `timestamp: number`：服务器时间戳（毫秒）

对齐原则：**客户端展示与提示以 `message` 为主**；`code/httpStatus` 为稳定的“机器可读信号”与观测分桶依据。

#### 2.3.2 `traceId` / `requestId` 格式

- `traceId`：**32 个 hex 字符（128-bit）**，推荐使用 UUIDv4 去掉横杠的小写形式（例如 `uuid.replace(\"-\", \"\")`）
- `requestId`：同上；在 WS 私信发送链路中 `requestId == traceId`（用于跨服务串联）

说明：文档中出现的“32 位 hex”均指“32 个 hex 字符”，不是 32-bit。

#### 2.3.3 trace header（HTTP）

- `X-Trace-Id: <traceId>`：跨服务透传主键
- `traceparent`：可选；下游缺失时允许仅依赖 `X-Trace-Id` 完成串联

#### 2.3.4 WS 回执（sendAck / sendError）

- `sendAck`：表示“通过治理校验且已进入处理队列”，不承诺已落库
- `sendError`：表示“本地校验/治理校验/系统不可用导致未受理”，必须包含可读 `message` 与稳定 `code`

## 3. 总体方案（推荐：入口治理 + 协议对齐）

采用“社区侧提供治理校验 API + IM WS 入口前置校验”的方式：

1) community-bootstrap 新增 **IM 私信发送治理校验 API**（普通 `/api/**`，JWT 鉴权）  
2) im-realtime 在 `sendPrivateText` 处理时调用该校验 API：  
   - 校验通过：才投递 Kafka command  
   - 校验失败：立即通过 WS 返回 `sendError`（并携带 message）  
3) im-core 对齐主站 `Result<T>` 协议并补齐全局异常映射；修复 `markRead` 脏写入问题。  
4) 前端：为 im-core HTTP 服务启用 `unwrapResultBody` 解析；同时接入 WS `sendAck/sendError` 反馈。

该方案可以在不改 Kafka 协议的前提下阻断“用户通过 IM 旁路治理规则”的风险面，并修复 read_state 数据完整性问题。

### 3.1 写入口覆盖矩阵（防旁路）

本次治理校验的“强约束入口”定义为：**所有用户可触达的私信写入口，都必须在投递 Kafka command 前完成治理校验**。

当前仓库内的私信写入口清单如下（以源码为准）：

- ✅ WebSocket：`im-realtime` `sendPrivateText`（用户直连入口）
  - 本方案在此处前置调用 community 校验 API
- ⛔ HTTP：`im-core` 当前无“发送私信”的 HTTP API（仅会话列表、历史消息、markRead 等）
- ⛔ 其他 producer：本仓库未发现除 `im-realtime` 外的 `SendPrivateTextCommandV1` 生产者

旁路风险主要来自“非预期 producer”（例如未来新增内部服务/脚本直接写 Kafka）。防护策略：

1) **Kafka ACL（推荐，运行期约束）**：限制 `ImTopics.COMMAND_PRIVATE_TEXT_V1` topic 的 producer 仅允许 `im-realtime`（或受控服务身份）写入。  
2) **代码层约束（研发约束）**：任何新增写入口必须复用同一治理校验逻辑；否则视为安全缺陷。  

上线要求（建议）：

- 生产环境将 Kafka ACL 作为“治理收敛上线前置条件”。验收口径：使用非 `im-realtime` 身份写入 `COMMAND_PRIVATE_TEXT_V1` 必须失败。
- 若运行环境无法提供 producer 身份隔离（ACL 不可用/过粗），必须在风险评估中明确承认残余风险：内部非预期 producer 仍可能绕过治理校验。

## 4. community-bootstrap：治理校验 API

### 4.1 Endpoint

为避免与 `/api/im/**`（通常会被 edge/ingress 路由到 im-core）冲突，新增独立路径：

- `POST /api/im-governance/private-messages/validate`

### 4.2 鉴权与身份

- 使用现有 `/api/**` 安全链：必须登录。
- **fromUserId 从 JWT.subject 解析**（`CurrentUser.requireUserId(authentication)`），不信任请求体传入的 fromUserId。

### 4.3 Request

```json
{ "toUserId": 123 }
```

### 4.4 校验逻辑（fail-closed）

顺序建议：

1) 参数：`toUserId > 0` 且 `toUserId != fromUserId`  
2) 禁言/封禁：复用 message 域现有 `UserModerationGuard.assertCanSendMessage(fromUserId)`（优先 fail，避免被禁言用户借此枚举目标用户存在性）  
3) 拉黑：复用 `BlockQueryApplicationService.isEitherBlocked(fromUserId, toUserId)`  
4) 目标用户存在：若不存在返回明确语义 message（例如“目标用户不存在”）  

### 4.5 错误语义（稳定 code + 可读 message）

本项目对错误的主要诉求是“**message 语义可读且稳定**”，不强制客户端依赖 HTTP status 或 code 分支；
但为了可观测性与客户端可预期处理，本 API 仍遵循统一 `Result` 协议：

- `Result.code`：使用稳定的数字码（优先复用 `CommonErrorCode` 的 400/401/403/404/500/503 等通用码）
- `Result.message`：**人类可读且表达明确语义**（例如“目标用户不存在”“你已被禁言，暂时无法发送私信”“双方存在拉黑关系，无法发送私信”）
- `Result.httpStatus`：尽量与 `code` 一致（但客户端不要求强依赖）

典型映射建议：

- `toUserId` 非法 / 自己给自己发：`code=400`，`message=参数错误`（或更具体）
- 目标用户不存在：`code=400`，`message=目标用户不存在`（刻意使用 400 对齐 legacy `/api/messages` 行为）
- 禁言/封禁：`code=403`，`message=你已被禁言.../账号已被封禁...`
- 拉黑：`code=403`，`message=双方存在拉黑关系，无法发送私信`
- token 失效：`code=401`，`message=未登录或登录已失效`
- 服务异常：`code=500/503`，`message=服务端异常/服务不可用`

### 4.6 Response

- 通过：`Result.ok()`（HTTP 200）
- 失败：抛出 `BusinessException` 交由 `GlobalExceptionHandler` 统一返回 `Result.error(...)`（HTTP status 与 message 不强耦合，但 message 必须可读）

## 5. im-realtime：WS 发送前治理校验 + sendAck/sendError

### 5.1 连接态保存 accessToken

`auth` 帧验签成功后，将原始 access token 保存在连接上下文中（仅用于调用 community-bootstrap 校验 API）。

安全约束：

- 严禁在日志/metrics/异常栈中输出原始 token（必须脱敏/不打印）。
- token 仅保存在内存连接态；连接断开即释放。

### 5.2 token 过期与重新鉴权（re-auth）

WS 长连接场景下，access token 可能在连接存活期间过期。为避免“突然无法发私信且只能重连”的体验，本方案支持在已鉴权连接上重复发送 `auth` 帧以刷新 token：

- 客户端在 refresh 成功后可再次发送：`{ type: "auth", accessToken }`
- 服务端行为：
  - token 合法且 `userId` 与当前连接绑定一致：更新连接态 token，返回 `auth_ok`
  - token 合法但 `userId` 不一致：返回 `auth_error` 并关闭连接（防止连接被劫持换号）
  - token 非法：返回 `auth_error`（必要时关闭连接）

### 5.3 sendPrivateText 行为

当收到：

```json
{ "type": "sendPrivateText", "toUserId": 2, "content": "...", "clientMsgId": "..." }
```

处理流程：

1) 基础校验（toUserId/content/clientMsgId/长度）失败：返回 `sendError`  
2) 调用 community 校验 API（带 `Authorization: Bearer <accessToken>`），并 **MUST** 透传 `X-Trace-Id: <traceId>`：  
   - 成功（Result.ok）：继续  
   - 失败（4xx/5xx 或网络超时）：返回 `sendError`，message 使用后端 `Result.message` 或降级文案（fail-closed：不投递 Kafka）  
3) 校验通过后投递 Kafka command：`SendPrivateTextCommandV1`（其中 `cmd.requestId = traceId`）  
4) 发送 `sendAck`（语义：已通过治理校验并被系统接收进入处理队列；不承诺已落库）

### 5.4 性能与韧性（避免把 IM 变成对 community 的放大器）

校验 API 为发送链路同步依赖，必须显式约束其开销与失败行为：

- 超时：短超时（建议 1s–2s），不做自动重试（避免放大抖动）
- 失败策略：fail-closed（不投递 Kafka），并返回可读错误 message
- 限流：im-realtime 按用户/连接维度做基本发送限流（例如 N 条/10s，可配置）
- 可选短缓存（谨慎）：对同一 `(fromUserId,toUserId)` 的“允许发送”结果做极短 TTL（例如 1s）缓存，默认关闭；开启时需接受“拉黑/禁言变更最多延迟 TTL 生效”的权衡

### 5.5 WS 协议

使用现有协议实现（已存在但未被使用）：

- `sendAck`：`{ type:"sendAck", cmd:"sendPrivateText", clientMsgId, requestId }`
- `sendError`：`{ type:"sendError", cmd:"sendPrivateText", clientMsgId, requestId, code, message, traceId }`

其中：

- `requestId`：服务端生成的 **32 个 hex 字符（128-bit）**（用于关联治理校验 HTTP 调用与后续 Kafka command）
- `traceId`：与 requestId 复用（`traceId == requestId`；用于 HTTP `X-Trace-Id` 透传与日志串联）
- `code`：稳定数字码（优先复用 `Result.code`；本地校验错误可使用 400/403 等通用码）

### 5.6 `sendError.code` 映射（可执行契约）

原则：WS 侧尽量复用 community 校验 API 的 `Result.code`；无后端响应时使用通用码表达“是否可重试”。

- 本地参数错误（toUserId/clientMsgId/content/长度不合法）：`code=400`
- 未鉴权/鉴权失效（连接未 auth 或 token 校验失败）：`code=401`
- 治理校验失败（禁言/封禁/拉黑/目标不存在等）：`code` 透传校验 API 的 `Result.code`（通常为 400/403）
- 校验 API 不可用/网络超时：`code=503`（提示“治理校验服务不可用，请稍后重试”）
- Kafka 生产失败/消息系统不可用：`code=503`（提示“消息系统不可用，请稍后重试”）
- re-auth userId 不一致：`code=403`（并主动断连）

可选增强（非必须）：增加 `retryable: boolean` 字段（仅用于 503/超时类），避免客户端猜测。

## 6. im-core：统一 Result<T> + 修 read_state 脏写入

### 6.1 /api/im/** 返回体对齐 Result

将 `ConversationController` 等 `@RestController` 的返回体统一为：

- `Result.ok(data)`（HTTP 200）
- 对错误：返回对应 HTTP status + `Result.error(code,message,httpStatus)`（含 traceId）

### 6.2 安全异常与参数错误的统一映射

补齐：

- Spring Security 401/403：自定义 `AuthenticationEntryPoint` / `AccessDeniedHandler` 返回 `Result`（`code=401/403`）
- `IllegalArgumentException`：映射为 `CommonErrorCode.INVALID_ARGUMENT`（`code=400`）
- `AccessDeniedException`：映射为 `CommonErrorCode.FORBIDDEN`（`code=403`）
- 其他未捕获异常：映射为 `CommonErrorCode.INTERNAL_ERROR`（`code=500`）

### 6.3 修复 markRead 脏写入

`POST /api/im/conversations/{conversationId}/read` 在写入 read_state 前：

1) 校验会话存在并加载参与者（权威来源为 `im_conversation.user_a/user_b`）  
2) 校验当前用户确为会话成员（避免仅依赖字符串格式解析）  
3) 通过后才允许更新 read_state（不存在则返回 `NOT_FOUND`，且不触发 read_state upsert）  

说明：这可以阻断“伪造包含自己 userId 的 conversationId”导致的 read_state 脏数据。

### 6.4 traceId 对齐

im-core 增加与主站一致的 header：

- `X-Trace-Id`
- `traceparent`

并在 `Result` 响应体回填 `traceId` 字段。

## 7. 前端改动点

1) `frontend/src/api/services/imCoreChatService.js`：改为使用 `unwrapResultBody` 解包 `Result<T>`  
2) `frontend/src/im/imRealtimeClient.js` + `ConversationDetailView.vue`：接入 `sendAck/sendError`，失败时展示 message（治理失败原因）  

### 7.1 发布兼容（前后端互不打断）

为避免 im-core 返回体从“裸 JSON”切换到 `Result<T>` 时出现发布顺序问题，前端对 im-core 的解析采取“双栈兼容”策略：

- 若响应体包含 `code/message/data`：按 `Result<T>` 解包  
- 否则：按 legacy 裸 JSON 兜底解析（仅覆盖本次涉及的会话列表/历史消息/read 接口形态）  

该策略使得前端可以先发布；随后 im-core 切换为 `Result<T>` 后再移除 legacy 解析。

落地判定规则（避免字段名碰撞误判）：

- `Result` 判定必须满足类型与字段齐全：  
  - `typeof body?.code === "number"`  
  - `typeof body?.message === "string"`  
  - `typeof body?.httpStatus === "number"`  
  - `typeof body?.traceId === "string"`  
  - `typeof body?.timestamp === "number"`  
  - 并且包含 `data` 字段（允许为 `null`）
- 对于会话列表这类 legacy 直接返回 array 的情况：若 `Array.isArray(body)` 则直接按 legacy 处理

## 8. legacy /api/messages 的误导治理

`/api/messages/**` 作为历史“站内私信”API，短期保留以维持兼容，但必须降低误用概率：

- 前端侧：`frontend/src/api/services/messageService.js` 明确标注为 deprecated（注释 + 文档说明），并确保不在新代码中引用  
- 文档侧：补充说明“私信统一以 IM 为准”的入口说明，避免同学误选接口  

## 9. 测试与验证计划

- community-bootstrap：
  - 单测：治理校验 API 的禁言/拉黑/用户不存在分支返回 message 正确
- im-core：
  - 单测：`markRead` 对不存在会话不写 read_state（可通过 repository 查询验证）
  - 单测：异常映射返回 `Result`（400/403/401）
- im-realtime：
  - 集成测试：当治理校验返回 403 时，WS 返回 `sendError` 且不投递 Kafka（可通过 mock client 或 embedded kafka 验证）

## 10. 风险与回滚

- 风险：im-realtime 依赖 community 校验 API 可用性；按设计为 fail-closed，可能导致短暂不可发私信。  
  - 缓解：校验 API 需轻量、超时短（例如 1–2s），并在 im-realtime 做明确错误反馈。  
- 回滚：可通过配置开关禁用 WS 侧治理校验（保留代码但短路），或临时让校验失败时 fail-open（不推荐，仅用于应急）。  

配置开关（建议，需审计）：

- `im.governance.enabled`：是否启用治理校验（默认 `true`）
- `im.governance.fail-closed`：失败是否阻断发送（默认 `true`；应急时可临时 `false`，必须时间盒 + 审计）
- `im.governance.timeout-ms`：校验 API 超时（默认 `1000`）
- `im.governance.allow-cache-ttl-ms`：允许发送短缓存 TTL（默认 `0` 表示关闭）
- `im.governance.rate-limit.window-ms` / `im.governance.rate-limit.max`：发送限流窗口与阈值（默认保守值）
