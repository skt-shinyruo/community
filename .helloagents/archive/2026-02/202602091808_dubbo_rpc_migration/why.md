# 变更提案：Dubbo RPC 服务间调用迁移（保留网关 HTTP 路由）

## 需求背景

当前项目是典型的“前端 → gateway → 各业务服务”的微服务形态，服务间同步调用主要通过 HTTP（`RestTemplate` / 少量 `WebClient`）访问下游的 `/internal/**` 或公开 `/api/**` 端点，并配套了：

- 统一响应封装：`com.nowcoder.community.common.api.Result`
- internal client 解包/错误映射与指标：`com.nowcoder.community.common.web.internalclient.InternalClientSupport`
- traceId 贯穿：入站 Filter/WebFilter + 出站 `TraceIdClientHttpRequestInterceptor`

现状主要问题/痛点：

1. **服务间调用语义仍被迫“HTTP 化”**：需要维护 baseUrl、path、headers、HTTP status 与 Result 的双层语义，对开发/排障与演进不友好。
2. **样板代码与配置分散**：各服务内存在多套 `*ServiceClient`、`*RestClientConfig`、`clients.*.base-url` 等配置与降级策略，重复且易漂移。
3. **调用治理落点不统一**：超时/重试/降级/指标的口径在不同 client 中各自实现，难以形成 SSOT。
4. **性能与资源利用**：同步 HTTP 调用在高并发下更容易受到连接/线程资源影响（尤其是展示聚合型读路径），并且链路追踪需要依赖 header 透传。

因此希望引入 **Dubbo** 作为服务间同步调用的统一 RPC 框架，通过 Zookeeper 做 registry，实现：

- 微服务之间同步调用从 HTTP 迁移到 Dubbo RPC
- 接口与 DTO 拆分为独立 `*-api` 模块（接口/DTO 独立，服务实现依赖 api）
- 治理能力（超时/重试/熔断/降级/指标/trace 透传）按最佳实践落地
- 对外接口保持不变：前端仍通过 gateway 的 `/api/**` 访问（不要求前端接入 Dubbo）

## 变更内容

1. **新增 `*-api` 模块**：按服务边界拆分对外提供的 RPC 接口与 DTO（例如 `user-api`、`social-api`、`content-api`、`analytics-api`），作为跨服务依赖的唯一入口。
2. **引入 Dubbo + Zookeeper registry（Solution 1）**：
   - 仅将 **“代码层服务间同步调用”** 切换为 Dubbo
   - gateway 继续保持 Spring Cloud Gateway 的 HTTP 路由转发（`/api/**` 不变）
   - Zookeeper 作为 Dubbo registry；Nacos 继续用于 Spring Cloud Gateway 路由/配置/服务发现（避免一次性推翻现有基础设施）
3. **将现有 internal HTTP client 替换为 Dubbo reference**：删除/下线服务间 `RestTemplate` 调用的 client 类，统一通过 Dubbo service interface 调用。
4. **补齐“调用治理最佳实践”的统一落点**：
   - 统一超时/重试/失败策略（按“读/写、关键/非关键”分级）
   - 统一 traceId 透传（Dubbo attachment）
   - 统一指标埋点（Micrometer + Prometheus 口径）

## 影响范围

- **模块（Module）**
  - 新增：`user-api`、`social-api`、`content-api`、`analytics-api`
  - 变更：`common`（Dubbo 过滤器/观测能力等公共能力）、`gateway`、`auth-service`、`user-service`、`social-service`、`content-service`、`search-service`、`message-service`、`analytics-service`
- **文件（Files）**
  - Maven 多模块：根 `pom.xml`、各 `*-api/pom.xml`、各服务 `pom.xml`
  - Dubbo 配置：各服务 `src/main/resources/application*.yml`、`deploy/docker-compose.yml`
  - 替换点：各服务的 `*ServiceClient` / `*InternalClient` / `*RestClientConfig` 等
- **API**
  - 对外：`gateway` 暴露的 `/api/**` **保持不变**
  - 对内：现有 `/internal/**` HTTP 端点可保留用于运维/兼容，但不再作为“服务间同步调用”主通道
- **数据（Data）**
  - 本次不引入 DB schema 变更；事件系统（Kafka/outbox）保持不变

## 核心场景

<a id="req-rpc-migration"></a>
### Requirement: 服务间同步调用迁移（HTTP → Dubbo）
**Module:** gateway / 各业务服务

<a id="scn-auth-user"></a>
#### Scenario: 认证链路（auth-service → user-service）
登录/刷新/注册/激活/找回密码等流程不再通过 HTTP `/internal/users/**` 调用 user-service，而是通过 Dubbo RPC 完成同等能力调用，保证超时与失败语义可控。
- 期望结果：认证相关流程功能一致、性能不劣化；下游不可用时错误语义清晰（可观测、可告警、可定位到 traceId）。

<a id="scn-user-social-profile"></a>
#### Scenario: 用户主页聚合读（user-service → social-service）
用户主页等展示读路径需要聚合获赞/关注/粉丝/关注状态等统计，不再通过 HTTP internal read API 获取，而改为 Dubbo RPC。
- 期望结果：读路径允许按配置 fail-open（降级为 0/false 并标记 degraded），但必须保留调用指标与告警口径。

<a id="scn-message-social-user"></a>
#### Scenario: 私信写路径校验（message-service → social-service / user-service）
私信发送需要检查双方拉黑关系，并解析/批量拉取用户摘要等信息；从 HTTP 迁移到 Dubbo。
- 期望结果：写路径保持 fail-closed（默认不允许无约束降级），必要时仅对“展示型补全”允许 fail-open。

<a id="scn-content-social-user"></a>
#### Scenario: 内容写路径校验与投影 bootstrap（content-service → social-service / user-service）
评论/帖子等写路径需要拉黑关系校验，且用户处罚投影缺失时需要向 user-service 做一次 bootstrap 回填；从 HTTP 迁移到 Dubbo。
- 期望结果：关键写路径不引入不受控重试；投影 bootstrap 的失败语义明确（避免把下游抖动放大成级联雪崩）。

<a id="scn-social-content-resolve"></a>
#### Scenario: 实体解析（social-service → content-service）
社交写路径需要对 POST/COMMENT 做 owner/postId 解析，避免信任客户端注入字段；从 HTTP 迁移到 Dubbo。
- 期望结果：解析服务具备严格超时与错误语义；错误可带 traceId 方便跨服务排障。

<a id="scn-search-content-reindex"></a>
#### Scenario: 搜索重建索引扫描（search-service → content-service）
search-service 重建索引时需要扫描帖子数据；从 HTTP 迁移到 Dubbo（后台任务，允许更宽松 timeout）。
- 期望结果：大 payload 场景具备合理超时；失败可重试但不产生重复副作用。

<a id="scn-gateway-analytics"></a>
#### Scenario: 网关 analytics 采集（gateway → analytics-service）
网关侧 UV/DAU 采集属于“可丢弃链路”，当前通过 WebClient 访问 analytics-service 的 `/internal/analytics/**`；迁移为 Dubbo 调用（仍保持 best-effort）。
- 期望结果：采集绝不影响主请求转发；队列满/超时/下游不可用时允许丢弃并记录指标。

<a id="req-api-modules"></a>
### Requirement: 接口与 DTO 模块化（`*-api`）
**Module:** 构建系统 / 各服务

<a id="scn-dep-convergence"></a>
#### Scenario: 跨服务依赖收敛
所有 Dubbo consumer 只依赖目标服务的 `*-api` 模块（接口 + DTO），不直接依赖对方服务实现模块，避免包名/DTO 漂移导致的隐式耦合。
- 期望结果：跨服务调用在编译期可发现破坏性变更；DTO 归属清晰、避免重复定义（如 moderation/status、like scan response 等）。

<a id="req-governance-observability"></a>
### Requirement: 调用治理与可观测性（最佳实践落地）
**Module:** common / 各服务

<a id="scn-governance"></a>
#### Scenario: 超时/重试/降级/指标/trace 的统一口径
为 Dubbo 调用建立统一治理规则（默认 timeout、retries、cluster 策略、线程池隔离、指标口径、trace 附件透传），并允许对少数“展示类读路径”进行受控降级。
- 期望结果：关键链路 fail-fast、非关键链路可降级；所有调用可观测（成功/错误/超时/降级），并可基于指标告警。

## 风险评估

- **风险：一次性切换导致回归范围大**
  - **缓解：** 在方案中明确“变更清单 + 分阶段验证点 + 最小可回滚策略”（例如保留 internal HTTP controller 但不再使用；保留必要的配置开关用于紧急回退）。
- **风险：Dubbo 异常与错误模型不一致**
  - **缓解：** 统一 RPC 返回协议（建议沿用 `Result<T>` 作为业务错误承载），并统一在 consumer 侧做 unwrap/错误映射与指标记录。
- **风险：超时/重试配置不当引发放大效应**
  - **缓解：** 默认禁用重试（`retries=0`），仅对明确幂等读请求启用 1 次重试；写请求使用 failfast；为展示读路径保留明确的 fail-open 降级点位。
- **风险：traceId 串线或丢失导致排障困难**
  - **缓解：** 在 `common` 提供 Dubbo consumer/provider Filter：consumer 注入 attachment；provider 注入 `TraceContext/MDC` 并 finally 清理。
- **风险：本地多服务运行端口冲突**
  - **缓解：** Dubbo 协议端口默认使用随机端口（或按服务配置独立端口），通过 registry 发现；Docker Compose 下通过服务名访问 Zookeeper。
