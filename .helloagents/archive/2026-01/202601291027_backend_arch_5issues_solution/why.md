# Change Proposal: 后端架构治理（解决 5 类系统性问题 + 扩展治理项）

## Requirement Background

当前仓库采用 Spring Boot 3 微服务形态（`gateway` 统一入口 + `*-service` 业务域服务 + `common` 基线库 + `frontend` SPA），同时存在同步 HTTP（internal API + `X-Internal-Token`）与异步 Kafka 事件两种协作方式。代码中已经具备 traceId、统一 `Result<T>`、internal-token、部分 outbox/幂等等工程化基线，但在以下“核心 5 类系统性问题”上仍存在明显风险：

1. **写路径跨服务同步耦合**：内容写入/私信写入等路径存在对下游服务的同步依赖（拉黑关系、禁言/封禁状态），容易在下游抖动时造成级联失败与可用性下降。
2. **错误码体系与 HTTP 语义混用**：既存在以 HTTP status 作为 `Result.code` 的公共错误码，也存在 10001+ 的业务错误码；现有异常映射会导致部分业务错误返回 HTTP 200，影响网关/监控/前端一致处理。
3. **网关职责膨胀 + 默认安全态不统一**：网关承载鉴权、CORS、traceId、审计、限流、统计采集等多种能力，且存在可配置 fail-open（允许“错误配置/依赖异常时放行”）的场景，生产安全默认态需明确为 fail-closed。
4. **内部调用治理与爆炸半径控制不足**：internal-token 机制已存在，但配置侧仍允许全局 token 兜底，导致单点泄露扩大影响范围；内部 HTTP client 的错误映射/超时/降级语义也需进一步统一。
5. **环境差异与文档 SSOT 漂移**：同一能力在不同环境（如 search 的存储后端）存在实现切换与语义差异，叠加文档与配置不一致，容易引入“环境差异 bug”与排障成本。

在进一步代码审计中，还发现若干“扩展治理项”（同样属于高风险/高收益，建议并入同一轮治理）：

- **R6 search-service 事件消费幂等存在“丢索引更新”风险**：对 eventId 做 insert-first 标记且标记点位在索引副作用之前；当 ES 写入失败或幂等表写入异常被吞掉时，可能触发“消息被 ack 但索引未更新”的丢失窗口。
- **R7 social-service DB 异常被吞掉导致 silent failure**：DB repository 将广义 `DataAccessException` 直接吞掉并返回 `false/0/空列表`，会把真实的依赖故障伪装成“业务结果”，削弱可观测性与正确性（与“DB 为 SSOT”的边界冲突）。
- **R8 敏感配置存在占位默认值与全局兜底**：部署配置对 `JWT_HMAC_SECRET` 提供占位默认值、对 internal-token 允许全局兜底（例如 `${INTERNAL_TOKEN:}`），在生产环境易发生“未显式配置但仍可启动”的安全隐患与爆炸半径扩大。
- **R9 登录态存储与会话安全基线存在隐式不安全默认值**：refresh token 默认兜底为内存存储（多实例/重启不可靠），refresh cookie 默认 `Secure=false` 在生产 HTTPS 下存在误配风险。
- **R10 跨服务枚举/常量散落导致契约不稳**：`entityType/targetType` 等关键枚举值在多个模块以魔法数字形式分散定义（1/2/3），一旦演进或误配会造成跨服务事件/业务语义不一致，排障困难。
- **R11 聚合接口存在 N+1（DB + RPC）性能风险**：以 message-service 会话列表为例，循环内多次 DB 查询与跨服务用户查询会在高并发下放大延迟与级联负载。
- **R12 事件版本治理不一致**：`EventEnvelope.version` 在部分消费者未校验，且“未知类型/版本”的处理策略不统一（跳过/入 DLQ/标记已消费），会降低事件演进能力并可能造成难以察觉的数据缺口。
- **R13 运维/管理接口入口双轨制**：同一运维能力同时存在 `/api/**/internal/**`（JWT 管控）与 `/internal/**`（token 管控）两套入口（例如 reindex），增加攻击面与授权策略复杂度。
- **R14 配置中心依赖与启动策略缺少“生产 fail-closed”保证**：当前各服务使用 `spring.config.import=optional:nacos:...`，当 Nacos 不可用/配置缺失时可能退化到本地默认配置运行，放大“默认值不安全/配置漂移”的风险。
- **R15 可观测性端点（actuator/prometheus）暴露与授权不一致**：各服务启用了 `management.endpoints.web.exposure.include=...prometheus`，但安全配置未统一放行/保护 `/actuator/prometheus`，会导致 Prometheus 抓取失败或未来扩展时误暴露。
- **R16 Kafka DLQ 发布链路缺少 fail-closed 保证**：现有 `DefaultErrorHandler` recoverer 在 DLQ 发布失败时仍可能提交 offset，形成“处理失败 + DLQ 未落地 + 消息丢失”的窗口；同时 DLQ 载荷缺少统一 schema/traceId。
- **R17 环境 profile 与配置覆盖策略不稳定**：仅 gateway 存在 `application-prod.yml`，但部署未显式设置 `SPRING_PROFILES_ACTIVE=prod`；一旦 profile 未生效，fail-open 等“开发友好默认值”可能在生产路径被误用。
- **R18 防旁路与 cookie 安全边界假设未固化**：gateway 实现了 OriginGuard，但 auth-service 自身未实现同等防护；若 auth-service 被误暴露或旁路访问，cookie/refresh 等敏感入口将依赖“网络假设”而非代码 fail-closed。
- **R19 API 直接返回 entity 导致字段暴露与契约脆弱**：例如 `comments/replies` 直接返回 `Comment` 实体（含 deletedBy/deletedReason 等治理字段），即便当前查询过滤 status=0，仍属于“未来易泄露/难演进”的接口设计风险。
- **R20 common 组件加载机制不一致（gateway 特例）**：多数服务通过 `scanBasePackages=com.nowcoder.community` 扫描 common 的 `@Component`，而 gateway 未扫描 common；导致 cross-cutting 能力容易出现“服务间不一致”，并阻碍后续把通用能力下沉到 common。
- **R21 对象级鉴权缺失导致 IDOR（Insecure Direct Object Reference）**：message-service 的会话详情与“标记已读”接口未校验资源归属（仅按 conversationId 或 ids 操作），任意登录用户可读/改他人私信与通知状态，属于高危越权。
- **R22 服务侧 IP 解析信任边界不一致**：auth-service 直接信任 `X-Forwarded-For` 作为客户端 IP，会导致登录限流/风控可被伪造 header 绕过（尤其在 auth-service 旁路访问或代理链路配置不严时）；与 gateway 的可信代理策略不一致。
- **R23 资源关系校验缺失导致跨资源访问与枚举**：content-service 的回复列表接口路径包含 postId，但实际查询仅按 commentId 获取回复，未校验 commentId 是否属于该 postId；当前虽为公共数据，但会破坏“路径语义=访问边界”的约束，并在未来引入权限/私密能力时放大数据泄露风险。
- **R24 输入校验与 payload 限额缺失（DoS/数据污染）**：多处 DTO 仅做 `@NotBlank/@Min` 等存在性校验，缺少字段长度、列表数量等上限控制；在网关/服务端未统一限制请求体与字段大小时，可能导致超长文本写入、异常 payload、内存/DB 压力放大以及潜在 DoS，需要以“默认拒绝”为原则固化校验与上限。
- **R25 头像上传与更新链路存在 fail-open 与不可验证窗口**：`frontend` 的头像上传 UI 在上传失败时仍会继续调用“更新头像”接口（demo 兜底逻辑），属于生产不可接受的 fail-open；同时 user-service 仅凭 fileName 更新头像 URL，缺少“fileName 与用户/上传 token 绑定”的可验证性与大小/类型限制，存在存储滥用与数据污染风险。
- **R26 敏感词过滤资源加载失败可静默退化**：content-service 的 `SensitiveFilter` 在词典资源缺失/读取异常时会静默跳过（无日志/无 fail-fast），会把“内容治理能力”从显式能力变为隐式假设；在生产中属于不可观测的安全/合规风险，应以 fail-closed（优先启动期 fail-fast）固化。
- **R27 HTTP 写接口缺少幂等与重复提交保护**：发帖/评论/私信等写接口在网络重试、浏览器重复点击、移动端弱网等场景下可能产生重复写入与重复事件，导致计数/通知/索引等二次副作用放大；需要引入 `Idempotency-Key` 语义与统一幂等存储策略，保证“同一请求只产生一次副作用”。
- **R28 internal 运维/内部写接口缺少防滥用治理（DoS/误触发/越权放大）**：`/internal/**` 中存在 reindex、outbox replay、用户密码修改/治理处置等高权限能力；仅依赖 internal-token 保护但缺少频率/并发/单飞（single-flight）与双人确认等机制，一旦 token 泄露或误配置暴露，影响面与破坏力极大，需要建立“高风险操作最小权限 + 可回滚 + 可审计”的治理基线。

用户已明确约束与取舍：
- 仅生成方案包（不改代码、不执行实现）。
- 生产默认 fail-closed。
- 跨服务状态采用最终一致（事件驱动 + 本地投影）。
- 错误语义采用 HTTP status（方案 A）：HTTP status 表达错误类别，`Result.code` 表达业务细分。
- 五项一起完成，无时间限制。

---

## Change Content

- **R1 写路径解耦（最终一致）**：将“禁言/封禁、拉黑关系”等跨域规则从“每次写入同步 RPC 校验”改为“事件驱动的本地投影（Read Model）”，写路径仅依赖本地状态；提供冷启动/纠偏的对账与回填机制。
- **R2 错误协议统一（HTTP status + Result.code）**：统一各服务异常到 HTTP status 的映射策略，消除业务错误 HTTP 200 的不一致；补齐内部调用在非 2xx 场景下的错误透传与语义保真。
- **R3 网关默认安全态收敛（fail-closed）**：网关的安全关键能力（Origin Guard、限流、IP 信任）在生产默认 fail-closed；非关键能力（统计采集）与转发链路严格隔离，避免引入下游依赖与链路抖动。
- **R4 internal-token 分域与内部调用标准化**：去除“全局 token 兜底”作为生产默认路径，强制按服务/segment 配置 token 并支持轮转；internal client 统一超时/错误映射/指标口径。
- **R5 环境差异与 SSOT 修复**：明确 dev/staging/prod 的能力矩阵（尤其是 search 存储与语义差异），同步修正文档与配置，建立可验证的契约测试，避免环境分叉。
- **R6 search 事件消费可靠性修复**：修正幂等点位与异常处理策略，确保至少一次语义下不会因幂等标记/DB 错误而丢索引更新。
- **R7 social DB 异常治理**：只对“唯一约束冲突”做幂等吞吐，其余 DB 异常应显式失败并可告警，避免 silent failure。
- **R8 敏感配置兜底治理**：移除占位默认值与 internal-token 全局兜底作为生产路径，强制显式配置与轮转策略，降低爆炸半径。
- **R9 登录态存储与会话安全基线**：refresh token 存储在非 dev 默认使用共享存储（建议 Redis）；refresh cookie 在生产强制 `Secure=true` 并明确 SameSite/Domain/Path 策略。
- **R10 跨服务枚举/常量 SSOT**：将 `entityType/targetType` 等关键枚举值在 common 统一定义，并通过测试/文档固化，消除魔法数字漂移。
- **R11 聚合接口 N+1 治理**：对典型聚合接口引入批量 SQL 与批量内部调用（优先 `/internal/**`），降低 DB 与 RPC 放大效应。
- **R12 事件版本治理一致化**：统一消费者对 `EventEnvelope.version` 的校验与 unknown handling 策略（DLQ/skip），并形成可演进的兼容约定。
- **R13 运维入口统一**：统一运维/管理能力的入口与授权（优先 gateway 作为唯一外部入口 + internal-token 作为下游最小权限），降低攻击面。
- **R14 配置中心与启动策略 fail-closed**：在生产环境将配置中心依赖改为“必需”，并对关键配置缺失做启动期校验，避免在 Nacos 不可用/配置缺失时静默退化运行。
- **R15 可观测性端点治理（actuator/prometheus）**：统一 `/actuator/prometheus` 的访问控制与抓取方案（推荐 Basic Auth 或内网专用端口），保证 Prometheus 抓取可用同时避免误暴露。
- **R16 Kafka DLQ fail-closed 与标准化**：统一 DLQ schema（含 traceId）并在 DLQ 发布失败时 fail-closed（不提交 offset、触发告警/熔断），避免“失败即丢”的窗口。
- **R17 环境 profile 与配置覆盖治理**：显式化 dev/staging/prod 的 profile 策略与覆盖层级（本地文件 vs Nacos），并在部署中强制启用生产 profile，确保 fail-closed 默认值在生产必然生效。
- **R18 防旁路与 cookie 安全边界固化**：对 cookie/refresh 等敏感入口引入“服务侧防护 + 网络侧约束”的双保险：auth-service 也具备 OriginGuard/CSRF 等等价能力，避免旁路绕过。
- **R19 API DTO 化与字段暴露控制**：对外 API 返回 DTO 而非 entity，显式控制字段白名单；治理/审计字段只在管理员接口或 internal API 暴露。
- **R20 common 自动装配化（Boot AutoConfiguration）**：将 cross-cutting 能力（启动期校验、DLQ 发布器、internal client 规范等）以 Boot AutoConfiguration 形式下沉到 common，并通过条件装配区分 servlet/reactive，消除 gateway 特例导致的不一致。
- **R21 对象级鉴权（OwnerGuard）与 IDOR 治理**：对所有“以 id/conversationId/ids 操作资源”的写接口与敏感读接口，强制在 service/DAO 层加入 owner 约束（SQL 级过滤），并提供统一的 OwnerGuard 断言与审计日志，默认 fail-closed（不匹配即 404/403）。
- **R22 服务侧 IP 解析一致化（可信代理）**：将“是否信任 XFF”的决策下沉为统一策略（与 gateway 一致），并用于登录限流、审计、风控等需要真实客户端 IP 的能力，避免因旁路/误配导致绕过。
- **R23 资源关系校验固化（Path Semantics）**：对“路径包含父资源 id”的接口强制校验父子关系（DB join/exists 校验），避免跨资源访问与枚举；把这类校验固化为可复用的断言与测试基线。
- **R24 输入校验与 payload 限额（防 DoS / 数据污染）**：对帖子/评论/私信/注册/密码重置等文本字段以及 ids/tags 列表统一长度与数量上限，并在网关与服务侧同时固化（validation + request body size limit），超限返回 400；作为错误协议统一（R2）的补充基线能力。
- **R25 头像上传与更新链路 fail-closed（可验证 + 限额）**：移除前端“上传失败仍更新”的 demo 兜底；服务端将 upload token 与 fileName 绑定到用户并设置大小/类型限制，更新头像必须可验证且失败即拒绝，避免存储滥用与数据污染。
- **R26 敏感词过滤资源加载 fail-fast（生产 fail-closed）**：在生产环境要求敏感词词典资源存在且可加载；缺失/读取异常时启动期失败（fail-fast），并输出可观测指标（词条数量、加载结果），避免静默退化。
- **R27 HTTP 写接口幂等与重复提交治理（Idempotency-Key）**：为关键写接口（发帖/评论/私信/治理动作等）引入 `Idempotency-Key` 幂等语义，配合共享幂等存储与响应缓存，实现“同 key 只产生一次副作用”，并在存储不可用时按 fail-closed 处理（关键写路径返回 503）。
- **R28 internal 运维/内部写接口防滥用治理（single-flight + 双人确认）**：对高风险 internal 操作引入更强访问边界（ops-token/来源网段/调用方 allowlist）、并发与频率控制、单飞锁（避免重复 reindex/replay）、以及“可选双人确认”机制；所有操作必须可审计与可回滚。

---

## Impact Scope

- **Modules：**
  - `common/`（错误码/异常映射、internal-token、internal client 规范）
  - `gateway/`（fail-closed 默认、安全边界、IP 信任、统计采集隔离）
  - `content-service/`（写路径依赖治理、本地投影、事件消费）
  - `user-service/`（处罚状态事件化/对外提供投影源）
  - `social-service/`（拉黑关系事件化/对外提供投影源）
  - `message-service/`（写路径依赖治理、本地投影、事件消费）
  - `search-service/`（存储后端切换语义、reindex 可用性、文档一致）
  - `frontend/`（统一处理非 2xx + `Result` 载荷、错误提示与刷新逻辑）
  - `deploy/`、`docs/`、`.helloagents/`（配置与 SSOT）

- **APIs：**
  - internal API（`/internal/<segment>/**`）：用于冷启动扫描/纠偏、投影回填、reindex 等运维能力
  - 外部 API（`/api/**`）：错误协议与网关安全默认态调整可能影响响应语义

- **Data：**
  - 新增/调整本地投影表（用户处罚状态、拉黑关系快照等）
  - 事件契约扩展（新增事件类型/载荷或复用现有 topic）

---

## Core Scenarios

### Requirement: R1 写路径跨服务同步耦合治理（最终一致）
**Module:** content-service / message-service / user-service / social-service

#### Scenario: 禁言/封禁用户写入拦截（不依赖 user-service 实时可用）
- 条件：用户被禁言/封禁后，写接口（发帖/评论/私信等）在本地投影中可判定
- 预期：返回 HTTP 403；并在投影滞后时提供纠偏（对账/回填）与观测告警

#### Scenario: 拉黑关系写入拦截（不依赖 social-service 实时可用）
- 条件：A 与 B 存在拉黑关系
- 预期：评论/私信等写接口返回 HTTP 403；本地投影缺失时按安全默认态处理

#### Scenario: 状态变更传播与最终一致窗口
- 条件：处罚/拉黑关系发生变化
- 预期：在定义的 SLA 内（如秒级到分钟级）传播到各写服务投影；落后时可观测、可纠偏

#### Scenario: 处罚动作（mute/ban）不在 DB 事务内执行跨服务副作用
- 条件：治理处置触发禁言/封禁等跨服务状态变更
- 预期：避免在本地 DB 事务中进行远程副作用调用（防止回滚/超时导致不一致）；通过 outbox/事件/after-commit 机制实现可靠投递与可补偿

### Requirement: R2 错误协议统一（HTTP status + Result.code）
**Module:** common / gateway / all services / frontend

#### Scenario: 登录失败（业务码）与鉴权失败（通用码）一致呈现
- 条件：用户名密码错误、token 过期、无权限访问
- 预期：HTTP status 分别表达 401/403/400 等类别；`Result.code` 保留细分业务码（例如 10001），前端统一展示 message 与 traceId

#### Scenario: 内部调用错误语义保真
- 条件：internal API 返回非 2xx 且 body 为 `Result`
- 预期：调用方能读取并透传 `code/message/traceId`，避免被 RestTemplate 直接吞为“服务不可用”

### Requirement: R3 网关默认安全态（fail-closed）与职责收敛
**Module:** gateway

#### Scenario: Origin allowlist 漏配/错误配置
- 条件：allowed-origins 为空或未包含请求 Origin
- 预期：生产默认拒绝（HTTP 403），并给出可观测告警指引

#### Scenario: 限流依赖异常（Redis 不可用）
- 条件：限流 Redis 故障/超时
- 预期：生产默认 fail-closed：返回可解释的错误（建议 503），而不是静默放行

#### Scenario: IP 可信边界（X-Forwarded-For）
- 条件：公网客户端伪造 `X-Forwarded-For`
- 预期：在未启用可信代理时不信任 XFF；启用时仅信任配置 CIDR 内的代理转发

### Requirement: R4 internal-token 分域与内部调用治理
**Module:** common / deploy / all services

#### Scenario: internal API 访问控制
- 条件：请求 `/internal/**` 未携带有效 `X-Internal-Token`
- 预期：返回 HTTP 403；token 轮转窗口内 current/previous 均有效

#### Scenario: 去全局 token 兜底（降低爆炸半径）
- 条件：服务未配置自身 `<segment>.internal-token`
- 预期：非开发环境启动失败或显式拒绝 internal 访问，避免误放行/误调用

### Requirement: R5 环境差异与 SSOT 修复
**Module:** search-service / deploy / docs / .helloagents/wiki

#### Scenario: search 存储后端一致性与可预期切换
- 条件：dev/staging/prod 使用不同 `search.storage`
- 预期：差异被明确记录（能力矩阵、语义差异、测试要求）；默认值与部署配置一致，不产生“隐式切换”

### Requirement: R6 search-service 事件消费幂等可靠性（避免丢索引更新）
**Module:** search-service

#### Scenario: ES 写入失败后可安全重试
- 条件：消费到 `POST_PUBLISHED/POST_UPDATED/POST_DELETED` 事件，ES 写入发生失败（超时/拒绝/连接异常）
- 预期：消息不应因“幂等已标记”而在后续重试中被跳过；恢复后应能补齐索引状态（允许重复 upsert/delete）

#### Scenario: 幂等表写入异常时不应吞掉并跳过消息
- 条件：幂等表（`search_consumed_event`）短暂不可用/写入失败
- 预期：应触发重试/DLQ（按配置），而不是返回“已消费”语义导致消息被 ack 丢失

### Requirement: R7 social-service DB 异常可观测与语义正确
**Module:** social-service / common

#### Scenario: DB 故障时不应伪装为“未点赞未关注数量为 0”
- 条件：DB 连接失败或 SQL 执行异常
- 预期：写操作应失败并返回可解释错误（建议 HTTP 503）；读操作可按业务选择降级，但必须可观测且显式标记 degraded

### Requirement: R8 敏感配置占位默认值与全局兜底治理
**Module:** deploy / gateway / auth-service / search-service / message-service / analytics-service / common

#### Scenario: 生产环境缺失密钥时必须失败（而非使用占位默认值）
- 条件：`JWT_HMAC_SECRET` 未显式配置
- 预期：服务启动失败或明确拒绝（避免使用占位默认值通过校验）

#### Scenario: internal-token 必须分域（避免全局 token 扩大爆炸半径）
- 条件：使用 internal API 调用（`/internal/**`）
- 预期：必须使用对应 segment 的 token（支持 current/previous 轮转），不允许使用全局 `INTERNAL_TOKEN` 作为生产兜底

### Requirement: R9 登录态存储与会话安全基线（refresh token / cookie）
**Module:** auth-service / gateway / deploy / frontend

⚠️ Uncertainty Factor: 当前生产是否会对 auth-service 做横向扩展/滚动重启频率未知（但该风险在未来几乎必然出现）。
- Assumption: 需要支持多实例与重启后的 refresh 可用性（更安全的默认选择）
- Decision: 将 Redis 作为 refresh token 存储默认实现（或非 dev 缺失配置即失败）

#### Scenario: auth-service 多实例/重启后 refresh token 仍可用
- 条件：auth-service 横向扩展或发生重启
- 预期：refresh token 存储必须是共享的（建议 Redis），不能默认落在单实例内存中；否则会造成“刷新失败/频繁重新登录”

#### Scenario: refresh cookie 在生产 HTTPS 下必须安全
- 条件：生产环境通过 HTTPS 对外提供服务
- 预期：refresh cookie 必须启用 `Secure=true`，并明确 `SameSite` 策略与域/路径边界；配合 gateway 的 Origin/CSRF 防护，避免被降级绕过

### Requirement: R10 跨服务枚举/常量 SSOT（entityType/targetType）
**Module:** common / content-service / social-service / user-service / message-service / docs

#### Scenario: entityType/targetType 值在全链路一致且可演进
- 条件：点赞/关注/评论/举报等跨域数据包含 entityType/targetType
- 预期：统一由 common 定义并在各服务复用；新增类型必须经过显式变更与兼容评审，避免魔法数字漂移

#### Scenario: 事件载荷中的枚举值可验证
- 条件：Kafka 事件 payload 含 entityType/targetType
- 预期：消费端对枚举值进行校验与指标记录；非法值进入 DLQ 或标记为 bad_event（可观测）

### Requirement: R11 聚合接口 N+1 治理（DB + RPC）
**Module:** message-service / user-service / common

#### Scenario: 会话列表不触发 N+1 DB 查询
- 条件：消息会话列表返回 N 条会话
- 预期：单次请求使用批量 SQL（group/IN）获取未读数/总数，避免每条会话 2 次额外查询

#### Scenario: 会话列表不触发 N+1 跨服务用户查询
- 条件：会话列表需要展示目标用户信息
- 预期：通过 user-service 提供批量用户摘要 internal API（或缓存），message-service 单次批量拉取用户摘要

### Requirement: R12 事件版本治理一致化（envelope version + unknown handling）
**Module:** common / content-service / social-service / message-service / search-service / user-service

#### Scenario: 消费端对 envelope version 统一校验
- 条件：收到非 v1 的 `EventEnvelope`
- 预期：按统一策略处理（建议进入 DLQ 或明确 skip 且不写入“已消费”记录），避免未来升级后无法回放

#### Scenario: 未知 type 的处理策略可控且可观测
- 条件：收到未知 `type`
- 预期：统一打点 + 按配置选择 DLQ/skip；避免静默丢弃造成数据缺口

### Requirement: R13 运维/管理入口统一（降低攻击面）
**Module:** gateway / search-service / content-service / docs / deploy

#### Scenario: reindex 等高风险运维能力只有一个外部入口
- 条件：触发 reindex/回填/对账等高成本操作
- 预期：外部仅通过 gateway 的管理员入口触发；gateway 再以 internal-token 调用下游 `/internal/**`，避免下游同时暴露 `/api/**/internal/**` 与 `/internal/**` 两套入口

#### Scenario: 运维入口具备审计、限流与可回滚
- 条件：管理员误操作或恶意触发
- 预期：必须有审计日志、严格限流与二次确认（可选），并可通过配置快速关闭入口

### Requirement: R14 配置中心与启动策略（生产 fail-closed）
**Module:** deploy / gateway / all services / docs

#### Scenario: Nacos 不可用/配置缺失时，生产不得静默退化启动
- 条件：生产环境启动时 Nacos 不可用、或关键配置项缺失
- 预期：服务应 fail-closed（启动失败或进入只读/维护态），避免使用默认值继续运行造成安全/语义漂移

#### Scenario: 关键配置缺失必须被启动期校验阻断
- 条件：密钥（JWT_HMAC_SECRET）、internal-token（分域）、外部依赖地址等缺失或为空
- 预期：非 dev 环境启动期直接失败，并输出明确的缺失项清单与修复指引（runbook）

### Requirement: R15 可观测性端点治理（actuator/prometheus）
**Module:** deploy / gateway / all services / observability

#### Scenario: Prometheus 可以稳定抓取指标（不依赖 JWT）
- 条件：Prometheus 抓取 `*/actuator/prometheus`
- 预期：抓取应成功且稳定（无 401/403）；授权方式明确（例如 Basic Auth 或内网专用端口）

#### Scenario: actuator 暴露面可控且可审计
- 条件：未来需要额外暴露 actuator 端点（如 metrics、env 等）
- 预期：默认拒绝并需要显式审批/配置；避免因“permitAll(/actuator/**)”等宽松策略导致误暴露

### Requirement: R16 Kafka DLQ fail-closed 与标准化
**Module:** common / message-service / content-service / search-service / deploy

#### Scenario: 消费失败后进入 DLQ 且不丢失
- 条件：消费者处理失败，重试耗尽需要进入 DLQ
- 预期：DLQ 发布成功后才能提交 offset；若 DLQ 发布失败则 fail-closed（停止提交、触发告警/退避），避免消息丢失

#### Scenario: DLQ 载荷具备统一 schema 与 traceId
- 条件：进入 DLQ 的消息需要排障与回放
- 预期：DLQ 记录包含 original topic/partition/offset、eventId（如有）、traceId（如有）、errorType/message、payload（可裁剪/脱敏）等字段，便于定位与回放

### Requirement: R17 环境 profile 与配置覆盖治理（确保 prod 策略生效）
**Module:** deploy / docs / all services

#### Scenario: 生产环境必须显式启用 prod profile
- 条件：生产部署或演练环境启动服务
- 预期：`SPRING_PROFILES_ACTIVE` 必须显式为 `prod`（或等价机制），避免“忘记设 profile → 走 dev 默认值”的安全事故

#### Scenario: 关键 fail-closed 默认值不能依赖“只在 prod profile 才存在的文件”
- 条件：配置中心不可用或配置缺失时回退到本地默认值
- 预期：安全关键默认值（例如网关限流 fail-closed、OriginGuard allowlist 为空 fail-closed）在生产路径必须可用且可验证（优先由本地 prod 文件兜底 + 启动期校验）

### Requirement: R18 防旁路与 cookie 安全边界固化（OriginGuard/CSRF）
**Module:** gateway / auth-service / deploy / docs

#### Scenario: auth-service 被旁路访问时仍不降低安全性
- 条件：auth-service 端口被误暴露或被内部恶意流量直接访问
- 预期：对 login/refresh/logout 等 cookie 会话相关入口，auth-service 也应具备等价的 OriginGuard/CSRF 防护（生产默认 fail-closed），不能仅依赖 gateway

#### Scenario: SameSite/OriginGuard 策略与前端部署形态一致
- 条件：前端与网关处于同站（same-site）或跨站（cross-site）部署
- 预期：cookie 的 SameSite/Secure 策略与 OriginGuard allowlist 必须成对设计；若必须跨站（SameSite=None），则 OriginGuard/CSRF 强度必须提升并在服务侧生效

### Requirement: R19 API DTO 化与字段暴露控制（避免 entity 直出）
**Module:** content-service / frontend / docs

#### Scenario: 公共评论/回复接口不暴露治理字段
- 条件：匿名用户访问帖子评论/回复列表
- 预期：返回 DTO 字段白名单（id/userId/content/createTime/targetId 等），不包含 deletedBy/deletedReason/deletedTime 等治理字段，避免未来误用/误暴露

#### Scenario: 接口契约可演进且不绑定表结构
- 条件：DB schema 新增治理字段或内部字段
- 预期：对外 API 的字段不随表结构自动扩散；通过契约测试保证前端兼容

### Requirement: R20 common 自动装配化（Boot AutoConfiguration）与一致性
**Module:** common / gateway / all services

#### Scenario: common 的 cross-cutting 能力在所有服务中一致生效
- 条件：新增通用能力（启动校验、DLQ 发布器、internal client 规范等）
- 预期：通过 Boot AutoConfiguration 自动装配在所有服务中生效，不依赖 `scanBasePackages`；gateway 也不再成为例外

#### Scenario: servlet/reactive 条件装配避免“错误类型的 Bean 被加载”
- 条件：gateway（reactive）与业务服务（servlet）共用 common
- 预期：servlet-only 的 Filter/Handler 不应在 reactive 应用装配；reactive-only 的组件不应在 servlet 应用装配

### Requirement: R21 对象级鉴权（OwnerGuard）与 IDOR 治理
**Module:** message-service / common / gateway / docs

#### Scenario: 非会话成员不能读取会话消息（conversationId 越权）
- 条件：登录用户请求 `/api/messages/conversations/{conversationId}`，但该 conversationId 不包含该用户（或该用户非 from/to）
- 预期：返回 404（推荐）或 403；不得返回任何消息列表；应记录安全审计（可选）

#### Scenario: 非接收方不能标记消息/通知为已读（ids 越权）
- 条件：登录用户调用 `/api/messages/read` 或 `/api/notices/read`，body 包含不属于该用户的 messageId/noticeId
- 预期：仅允许更新属于该用户的记录（to_id=userId），其余 id 被忽略或直接 fail-closed；不得更新他人记录

#### Scenario: 对象级鉴权必须在 DB 层可证明（避免仅 controller 校验）
- 条件：后续新增接口或绕过 controller 直接调用 service/mapper
- 预期：owner 约束应在 SQL 层体现（where to_id=? / (from_id=? or to_id=?))，并提供单测/集成测试固化

### Requirement: R22 服务侧 IP 解析一致化（可信代理）
**Module:** auth-service / gateway / common / deploy

#### Scenario: 伪造 X-Forwarded-For 不能绕过登录限流
- 条件：公网客户端直接访问 auth-service（旁路）或代理链路未配置可信网段时，伪造 `X-Forwarded-For`
- 预期：服务侧必须忽略不可信 XFF，使用 `remoteAddr`；登录限流应基于真实来源 IP（或在旁路场景直接拒绝）

#### Scenario: 仅在可信代理链路中信任 XFF
- 条件：请求来自配置的可信代理 CIDR（例如 ingress / gateway / internal LB）
- 预期：允许解析 XFF 的第一个公网 IP（或按约定规则），并将该结果用于限流/审计/风控

### Requirement: R23 资源关系校验固化（Path Semantics）
**Module:** content-service / common / docs

#### Scenario: replies 接口必须校验 commentId 属于 postId
- 条件：请求 `GET /api/posts/{postId}/comments/{commentId}/replies`
- 预期：若 `commentId` 不属于该 `postId`，返回 404（推荐）或 400；避免跨帖查询与枚举

#### Scenario: 资源关系校验在 service/DAO 层可复用
- 条件：后续新增“跨资源嵌套路径”的接口（例如 post->comment->reply）
- 预期：有统一的断言方法（exists/join 校验）与回归测试，避免每个 controller 手写且易遗漏

### Requirement: R24 输入校验与 payload 限额（防 DoS / 数据污染）
**Module:** gateway / common / auth-service / content-service / message-service

#### Scenario: 超长文本输入被拒绝（post/comment/message/registration）
- 条件：请求 body 中 title/content/comment/content/password 等字段超出约定上限
- 预期：服务侧在 validation 阶段直接拒绝并返回 HTTP 400；不得写入 DB 或触发副作用（事件/缓存）

#### Scenario: 超长列表/嵌套参数被拒绝（ids/tags 等）
- 条件：请求包含过长 ids 列表或 tags 数量/单个 tag 长度超限
- 预期：返回 HTTP 400，并记录指标（bad_request/oversize），避免大 SQL 与内存膨胀

### Requirement: R25 头像上传与更新链路 fail-closed（可验证 + 限额）
**Module:** frontend / user-service / deploy

#### Scenario: 上传失败不能仍然更新头像（前端 fail-closed）
- 条件：上传到对象存储失败（网络/Token 无效/超时）
- 预期：前端必须停止并提示错误；不得继续调用“更新头像”接口写入 DB（避免不可验证的脏数据）

#### Scenario: 头像 fileName 必须绑定当前用户且可验证
- 条件：用户调用 `PUT /api/users/{userId}/avatar` 传入任意 fileName（非本次签发/非本人）
- 预期：服务端必须拒绝（HTTP 400/403）；只能更新为“服务端刚签发给该用户的 fileName”（或通过回调/存在性校验可证明已上传）

#### Scenario: 上传 token 必须限制大小/类型（防滥用）
- 条件：用户使用 upload token 上传超大文件或非图片文件
- 预期：对象存储侧直接拒绝（优先），服务侧也应限制并在文档中固化上限（与 R24 的 payload 限额策略一致）

### Requirement: R26 敏感词过滤资源加载 fail-fast（生产 fail-closed）
**Module:** content-service / common / deploy

#### Scenario: sensitive-words 资源缺失时 prod 必须 fail-fast
- 条件：发布包缺失 `sensitive-words.txt` 或读取异常（编码/权限/打包问题）
- 预期：生产环境启动期直接失败并输出明确原因（而非静默降级为“不过滤”）

#### Scenario: 敏感词词典加载结果可观测
- 条件：服务启动完成
- 预期：输出词条数量与加载结果的指标/日志，便于验证治理能力是否生效（避免“配置/打包漂移”）

### Requirement: R27 HTTP 写接口幂等与重复提交治理（Idempotency-Key）
**Module:** frontend / gateway / common / content-service / message-service

#### Scenario: 重复提交发帖/评论不会产生重复数据
- 条件：同一用户在弱网/超时重试/重复点击下，对 `POST /api/posts`、`POST /api/posts/{postId}/comments` 发起重复请求（携带同一 `Idempotency-Key`）
- 预期：仅首次请求产生 DB 写入与事件副作用；后续重复请求返回与首次一致的结果（例如同一个 postId/commentId），且不会重复增加计数/通知/索引副作用

#### Scenario: 重复提交私信不会产生重复消息
- 条件：同一用户对 `POST /api/messages` 发起重复请求（携带同一 `Idempotency-Key`）
- 预期：仅产生一条消息记录；重复请求返回相同语义结果（成功且不重复写入）

#### Scenario: 幂等存储不可用时关键写接口 fail-closed
- 条件：幂等存储（例如 Redis）不可用或超时
- 预期：对标记为“必须幂等”的关键写接口返回 HTTP 503（并可观测）；不得在“无法保证幂等”的情况下继续写入造成重复副作用

### Requirement: R28 internal 运维/内部写接口防滥用治理（single-flight + 双人确认）
**Module:** common / search-service / content-service / social-service / user-service / gateway / deploy

#### Scenario: reindex 必须单飞且可审计
- 条件：多次触发 `/internal/search/reindex` 或 gateway 管理入口 reindex
- 预期：同一环境同一时间仅允许一个 reindex job 运行（single-flight）；重复触发返回“已有 job”或 409；必须记录审计日志与 traceId/jobId

#### Scenario: outbox replay 属于高风险操作，必须强保护
- 条件：触发 `/internal/*/outbox/replay`
- 预期：除 internal-token 外，必须额外满足 ops-token/来源网段/频率限制（至少其一，推荐组合）；默认关闭或 require-break-glass；并记录审计与指标

#### Scenario: 内部写接口按最小权限分域（避免 token 泄露扩大爆炸半径）
- 条件：`/internal/users/**` 等内部接口被误暴露或 token 泄露
- 预期：高权限写操作（改密码/治理处置）必须使用独立 token 或调用方 allowlist；read-only 与 ops 分离，避免一个 token 同时拥有“全读+全写”能力

---

## Risk Assessment

- **Risk：错误协议调整引发兼容问题（前端/内部调用/网关）**
  - **Mitigation：** 设计兼容窗口（双语义/灰度开关）、补齐前端对非 2xx 的 `Result` 解析、统一 internal client 错误读取策略
- **Risk：事件驱动投影存在传播延迟与短暂不一致**
  - **Mitigation：** 定义最终一致 SLA、落后告警、定时对账回填、幂等与顺序性约束
- **Risk：token 分域改造导致配置复杂度上升**
  - **Mitigation：** 提供 runbook 与模板化配置，支持轮转与回滚；在非 dev 环境做启动期校验
- **Risk：网关 fail-closed 可能在依赖故障时扩大不可用范围**
  - **Mitigation：** 精确区分“安全关键路径”与“可降级路径”，并明确 503/429 的语义与告警策略
- **Risk：search 事件消费幂等策略调整影响回放/重试行为**
  - **Mitigation：** 明确至少一次语义与幂等点位；补齐“ES 故障→恢复”回归测试；对不支持版本/类型的丢弃策略给出 runbook
- **Risk：social-service 由 silent failure 改为显式失败后影响用户体验**
  - **Mitigation：** 读路径允许按业务降级但需显式打点；写路径优先正确性与可观测性，必要时通过网关/前端提示“服务繁忙”
- **Risk：移除占位默认值导致本地/测试环境启动门槛上升**
  - **Mitigation：** 为本地 compose 提供 `.env.example` 与默认开发密钥生成脚本；在文档中明确“仅 dev 可使用示例密钥”
- **Risk：枚举/常量统一改造影响面大**
  - **Mitigation：** 先在 common 定义并以测试固化，再逐模块迁移；迁移期间保持兼容（旧值仍可解析），并通过指标观测非法值
- **Risk：聚合接口优化涉及 DB 查询重构**
  - **Mitigation：** 以 message-service 为试点，先做可回滚的批量查询与批量 user 查询；用压测/指标验证收益后再推广
- **Risk：事件版本治理策略改变可能导致 DLQ 量上升**
  - **Mitigation：** 先在灰度环境开启“unknown→DLQ”并观测；对历史遗留消息提供回放/清理 runbook
- **Risk：运维入口统一需要调整调用与权限模型**
  - **Mitigation：** 保留短期兼容窗口（双入口但强告警/即将下线提示），最终收敛到单入口并记录 ADR
- **Risk：生产启动策略从“可降级”改为 fail-closed 可能导致部署失败暴露更多配置问题**
  - **Mitigation：** 提供 runbook 与健康检查脚本；在 staging 先演练并补齐必需配置；通过明确报错缩短排障时间
- **Risk：为 /actuator/prometheus 增加授权会导致 Prometheus 抓取失败**
  - **Mitigation：** 先在同一版本同步更新 Prometheus scrape 配置（Basic Auth/Token）；提供回滚开关与兼容窗口
- **Risk：DLQ 发布 fail-closed 可能导致消费组短暂卡死（offset 不前进）**
  - **Mitigation：** 为 DLQ 发布失败设置告警与熔断/暂停策略；必要时提供“本地落盘/DB outbox 作为 DLQ 兜底”方案，避免长期阻塞
- **Risk：强制启用 prod profile 可能暴露出大量历史“依赖默认值”的配置缺口**
  - **Mitigation：** staging 先演练并补齐配置；提供启动期缺失项清单与脚本化检查；保留临时兼容开关但默认关闭
- **Risk：在 auth-service 增加 OriginGuard/CSRF 可能影响部分客户端（非浏览器/内部调用）**
  - **Mitigation：** 明确仅覆盖 cookie 会话敏感入口；对非浏览器调用提供 internal-token 或 mTLS 路径；给出白名单与回滚策略
- **Risk：评论/回复 API DTO 化可能导致前端字段读取变更**
  - **Mitigation：** 提供兼容期（双字段或版本化）；用契约测试固定返回结构；先在前端适配再切换后端输出
- **Risk：引入 Boot AutoConfiguration 可能出现 Bean 冲突或装配顺序问题**
  - **Mitigation：** 使用条件装配（servlet/reactive）与显式 `@AutoConfiguration(before/after)`；在各服务 smoke test 中验证启动与核心链路
- **Risk：修复 IDOR 会改变部分接口返回（从“能读/能改”变为 404/403），可能影响现有前端与脚本**
  - **Mitigation：** 先在前端处理 404/403 并给出友好提示；在灰度环境回归验证；为审计与告警提供观测指标（越权尝试次数）
- **Risk：统一 IP 解析策略可能影响现有代理链路下的限流效果（例如 NAT/多级代理）**
  - **Mitigation：** 明确可信代理 CIDR 与解析规则；在 staging 回放真实流量头部进行验证；提供观测指标（ip_source=remote/xff）并灰度切换
- **Risk：新增资源关系校验可能在边界数据上引发 404（例如历史脏数据/缺失父资源）**
  - **Mitigation：** 将校验返回统一为 404（减少信息泄露）；对异常比例做指标监控并在数据修复后收敛
