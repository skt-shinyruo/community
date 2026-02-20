# 变更提案：common 模块拆分与复用漂移治理

## Requirement Background

当前仓库存在两类长期演进风险，且已经在代码中形成“可观测的耦合/重复”：

1. **common 共享内核过重**：`common/` 同时承载了返回协议/异常处理、事件契约（envelope + payload）、Dubbo 透传与埋点、幂等/任务护栏等基础设施能力，并且还包含各业务域错误码枚举（`*ErrorCode`）。这会把“跨域契约”和“基础设施实现”混在一起，导致服务自治演进被迫随 common 统一升级，且 common 容易演化为“什么都往里放”的耦合源头。
2. **关键横切能力重复实现导致漂移**：多个服务重复定义 `JwtProperties`（`security.jwt` 前缀）与 Prometheus basic-auth 用户/actuator 安全链；outbox/可靠投递在 `user/content/social` 等服务各自实现一套高度相似的结构（`OutboxEvent/Mapper/Service/RelayJob/Properties`），长期迭代中极易出现“细微差异漂移”（超时、放行路径、指标口径、重试策略不一致），排障成本会持续上升。

本提案选择的治理方向为：**“按职责拆分 common + contracts/starter 化横切能力”**，在保持运行期行为一致的前提下，降低跨域耦合与重复漂移风险，并对未来演进设置结构性护栏。

## Change Content

1. **按职责拆分 common：** 将与“跨域稳定协议”有关的内容独立成专用模块（contract），将与“横切基础设施”有关的内容独立成 starter/infra 模块，common 不再承载域语义与全量基础设施。
2. **域错误码归属到域：** 各业务域的 `*ErrorCode` 不再集中在 common；域内实现仅依赖自身域错误码 + `CommonErrorCode`（基础通用码）。跨服务调用通过 `Result.code/message` 透传，不要求消费者 import 生产方错误码枚举。
3. **事件契约按域拆分：** 事件 envelope/parser（通用）与 payload/type/topic（域）解耦；payload/type/topic 由生产方域的 `*-api`（或专用事件 contract 模块）提供，避免 common 变成事件 SSOT 的唯一承载点。
4. **横切安全/观测能力 starter 化：** 统一提供 JWT 解码器、Prometheus basic-auth 用户与 actuator 安全链（Servlet/Reactive 兼容），服务侧只保留“业务 API 授权矩阵”与少量差异化配置。
5. **Outbox 能力模块化：** 抽出通用 outbox 核心（实体/mapper/relay/job/metrics/properties），并通过配置/扩展点适配各服务 topic 与 producer 信息；删除各服务重复实现，统一口径与指标。
6. **防回潮门禁：** 增加结构性约束（测试/架构规则），禁止未来再次把域语义/重复配置“塞回 common”，也禁止跨域 import 域错误码与事件 payload。

## Impact Scope

- **Modules：**
  - 受影响：`common/`、`gateway/`、`auth-service/`、`user-service/`、`content-service/`、`social-service/`、`message-service/`、`search-service/`、`analytics-service/`、`*-api/`
  - 新增：若干 contract/infra/starter 模块（详见 how.md）
- **Files：** 多模块会发生 import/依赖迁移与重复类删除（`JwtProperties`、`prometheusUserDetailsService`、`outbox/*` 等）。
- **APIs：** 不引入新的业务对外 API；主要为构建/依赖与内部代码结构调整。
- **Data：** outbox 表结构需要核对并保持一致（如存在差异，需在迁移期补齐/适配）。

## Core Scenarios

### Requirement: R1-common-boundary
**Module:** build/common
收敛 common 的职责边界，使其不再承载域语义与全量基础设施，实现“显式依赖、按需引入”。

#### Scenario: S1-common-slim
在完成迁移后：
- common 不再包含各业务域 `*ErrorCode` 与跨域事件 payload/type/topic 的实现。
- 业务服务仅引入所需的 contract/infra/starter 模块，不再“默认依赖整个 common 大包”。

### Requirement: R2-security-starter
**Module:** gateway + all services
统一 JWT/actuator(prometheus basic-auth) 的横切实现，减少重复与漂移。

#### Scenario: S2-actuator-consistency
在完成迁移后：
- 所有服务的 `/actuator/health`、`/actuator/info` 放行策略一致。
- 所有服务的 `/actuator/prometheus` 都需要 `PROMETHEUS` 角色的 basic-auth，且用户来源与配置键一致。
- JWT HMAC secret 的校验规则与错误返回口径一致（Servlet/Reactive 均覆盖）。

### Requirement: R3-outbox-module
**Module:** user/content/social/message (producer/consumer)
统一 outbox 可靠投递实现与指标口径，消除多套实现带来的策略漂移风险。

#### Scenario: S3-outbox-unified
在完成迁移后：
- `user/content/social` 等服务不再持有各自的 `outbox/*` 重复实现，改为依赖统一 outbox 模块。
- outbox 重试/退避/claim 策略与指标口径一致，可通过配置按需调整而不分叉代码。

### Requirement: R4-event-contracts
**Module:** *-api + producers/consumers
按域拆分事件契约，降低 common 作为事件 SSOT 的耦合度。

#### Scenario: S4-domain-event-contract
在完成迁移后：
- 事件 payload/type/topic 由生产方域的 contract 提供，消费者按需依赖相应域 contract。
- `EventEnvelope`/parser 的通用能力独立为基础 contract，不与 domain payload 混放。

### Requirement: R5-errorcode-ownership
**Module:** all services + gateway
域错误码只在域内定义与使用；跨服务只透传 code/message，不进行“跨域枚举依赖”。

#### Scenario: S5-no-cross-domain-errorcode-import
在完成迁移后：
- 不再出现 `gateway` 或其他服务直接 import 其他域的 `*ErrorCode`（例如 gateway import SearchErrorCode）。
- 对于跨服务错误语义，统一通过 `Result.code/message` 与 code 段约定完成归因与观测。

## Risk Assessment

- **Risk：** 大范围依赖与包迁移可能导致编译失败、Bean 冲突、auto-config 顺序问题、以及配置键/指标口径变更带来的运行期不一致。
- **Mitigation：**
  - 采用“先新增模块与并行接入 → 再批量迁移 → 最后删除重复实现”的分阶段策略，每一步都以 `mvn test` 作为门禁。
  - 对关键行为（actuator 安全链、JWT secret 校验、outbox 重试/claim）补充最小集成/契约测试，确保迁移过程不改变运行期语义。
  - 增加结构性门禁（架构规则/测试）防止回潮。

