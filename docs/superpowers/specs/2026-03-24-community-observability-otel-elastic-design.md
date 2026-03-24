# Community Observability (OpenTelemetry + Elastic) Design Spec

**Date:** 2026-03-24
**Status:** Approved for planning
**Owner:** Codex

---

## 1. 背景

当前仓库已经具备一部分可观测性基础：

- 部署侧已有日志/指标文档与本地观测 profile，当前默认方案是 `Promtail -> Loki -> Grafana` 与 `Prometheus -> Grafana`
- `community-gateway` 已有入口访问日志
- `community-app` 已有写请求审计日志、统一异常日志、`traceId` 注入与透传
- IM 侧已有部分 Kafka / WebSocket / 异常日志

这些基础能支撑基础排障，但离“完整、统一、可长期演进”的日志体系还有明显差距：

1. 日志格式没有统一 schema，主要依赖手写字符串前缀和自由文本
2. `traceId` 已存在，但没有升级到标准化的 trace / log correlation 体系
3. 业务成功路径和关键状态变化日志不足，入口日志、异常日志、补偿日志之间不成体系
4. 部署侧当前主要围绕 Loki 设计，尚未形成统一的 logs / metrics / traces 模型
5. 高频链路、异步链路、安全审计链路缺少统一分类、字段约束和后续告警消费方式

用户已经明确确认新的目标方向：

- 采用 `OpenTelemetry + Elastic`
- 以“未来生产可直接沿用”为目标设计，而不是只服务本地调试
- 接受生产环境统一结构化 JSON 日志，本地开发可保留可读文本格式
- 方案不仅覆盖应用内日志规范，也覆盖采集、存储、检索、告警、分期实施与验收

本设计的目标是在不推翻当前服务边界与部署习惯的前提下，把仓库的可观测性体系从“零散日志 + 局部 traceId”演进为“OTel 采集 + Elastic 统一消费”的生产级方案。

---

## 2. 目标

### 2.1 核心目标

1. 统一后端四个核心服务的 `logs / metrics / traces` 采集路径
2. 以 `OpenTelemetry` 作为采集标准，以 `Elastic Observability` 作为统一后端
3. 保留现有 `X-Trace-Id` 兼容能力，但标准观测主键升级为 `trace.id`
4. 统一日志字段模型，形成稳定的结构化检索、看板和告警基础
5. 明确哪些事件必须记录、哪些事件禁止记录、哪些高频链路需要采样或降级
6. 交付分期实施方案，允许按阶段落地、验证和回滚

### 2.2 运维与排障目标

本方案落地后，至少应支持以下排障方式：

- 按 `trace.id` 串联 `community-gateway -> community-app -> im-core / im-realtime` 的一次请求
- 按 `service.name` 查看各服务的错误率、延迟、吞吐
- 按 `community.category / community.action / community.outcome` 查询关键业务事件
- 按 `community.job_id / community.event_id / community.topic` 排查异步链路
- 按 `user.id` 查询具有审计意义的敏感操作历史

### 2.3 非目标

本轮设计不包括以下内容：

- 前端 RUM / 浏览器端观测体系
- 基础设施全量监控平台重构
- 切换现有身份体系或引入 OIDC / SSO
- 一次性重构所有服务的业务日志
- 立即淘汰现有 Loki 相关文档和本地能力

本轮是“统一后端服务可观测性体系”的设计与分期方案，不是整个组织级观测平台重建。

---

## 3. 现状事实与约束

### 3.1 当前部署与文档事实

- 仓库已有 `docs/OBSERVABILITY.md`，当前默认观测方案围绕 `Promtail -> Loki -> Grafana` 与 `Prometheus -> Grafana`
- 本地/示例部署已有 observability 相关 profile 和端口约定
- 当前文档已把 `traceId`、审计日志和 Grafana 检索作为主要排障入口

这意味着新方案不能假设仓库里“完全没有观测基础”，而应该在现有文档和部署模式上演进。

### 3.2 当前日志事实

当前已有的关键日志点包括：

- `community-gateway` 入口访问日志
- `community-app` 写请求审计日志
- `community-app` 统一异常日志
- IM 侧 Kafka / WebSocket 失败日志与少量调试日志
- 搜索、outbox、积分投影、通知投影等模块的部分异步失败日志

但当前也存在几个明显问题：

- 绝大多数业务类没有 logger，日志点集中在边界层与补偿层
- 很多日志依赖类似 `[audit]`、`[search]`、`[outbox]` 这样的字符串前缀做分类
- 缺少统一字段字典与结构化约束
- 高频链路尚未统一定义“什么打 `info`、什么只打 `debug`、什么该走采样”

### 3.3 当前 trace 上下文事实

- `community-gateway` 会生成/透传 `X-Trace-Id`
- `community-app` 已有 `TraceIdFilter` 和 `TraceContext`，会把 traceId 放进 ThreadLocal 与 MDC
- `im-core` 目前仍主要基于 ThreadLocal TraceId，没有对齐到统一 MDC 输出能力

这意味着新方案不应直接废弃现有 `X-Trace-Id`，而应在一段时间内兼容：

- 入口继续支持 `X-Trace-Id`
- 观测平台中的标准字段以 `trace.id` 为主
- 过渡期允许保留 `community.legacy_trace_id`

### 3.4 当前业务与部署边界约束

本仓库的后端服务边界当前是：

- `community-gateway`：统一 HTTP / WS edge
- `community-app`：主业务单体
- `im-core`：IM 核心服务（Kafka / DB / internal API）
- `im-realtime`：IM 实时服务（WS / push / Kafka）

本方案必须尊重这些边界，不以“统一观测”为名推动额外的业务重构。

---

## 4. 方案对比

### 4.1 方案 A：只补结构化日志，日志仍走单独采集

做法：

- 后端统一 JSON 日志
- trace / metrics 与 logs 分开处理
- 日志继续沿用专用日志采集链路

优点：

- 改造面较小
- 可以较快统一日志格式

缺点：

- `logs / metrics / traces` 仍然分裂
- trace 与日志的关联配置会额外复杂
- 长期会形成两套采集心智

### 4.2 方案 B：`OTel instrumentation + OTel / EDOT gateway collector + Elastic`

做法：

- 应用统一通过 OTel agent / SDK 发送 `traces / metrics`
- 应用继续输出结构化日志，由 gateway collector 统一接入为 logs signal
- 统一经过 gateway collector 进行补全、治理、采样、导出
- Elastic 作为统一后端

优点：

- 采集层统一
- 可逐步扩展 traces / metrics / logs，而不是只补日志
- 便于跨服务关联、采样、资源属性治理
- 后续后端替换成本更低

缺点：

- 需要设计 collector 拓扑、字段 schema 和迁移步骤
- 前期会多一个网关 collector 组件

### 4.3 方案 C：全面切换到更重的一体化平台迁移

做法：

- 同时引入更多观测域，如前端 RUM、基础设施、完整 APM 套件
- 把本轮工作扩展为整体观测平台升级项目

优点：

- 最终形态完整

缺点：

- 远超本轮范围
- 极易导致第一阶段节奏失控

### 4.4 推荐方案

采用方案 B。

原因：

1. 它最符合用户明确确认的 `OpenTelemetry + Elastic` 方向
2. 它能保留现有服务边界与部署模式，不要求一次性推翻仓库结构
3. 它允许先以 agent + 结构化日志拿到高收益，再逐步补充精细埋点
4. 它比“只补日志”更接近生产级全链路观测，又比“全面平台迁移”更可控

---

## 5. 总体设计

### 5.1 目标架构

目标架构如下：

```text
community-gateway / community-app / im-core / im-realtime
    -> OTel Java agent
    -> OTLP (traces / metrics)
    -> observability-gateway-edot-collector
    -> Elastic Observability

community-gateway / community-app / im-core / im-realtime
    -> stdout structured logs
    -> docker container json logs
    -> filelog receiver in observability-gateway-edot-collector
    -> Elastic Observability
```

第一阶段采用“单 gateway collector”的拓扑，而不是多级 collector 拓扑。原因：

- 应用配置简单
- 本地 `docker compose` 易于接入
- 统一治理点清晰
- 以后如需扩容，仍可演进成 agent-side collector + gateway collector 双层结构

### 5.2 采集层职责

应用层负责：

- 输出结构化业务日志
- 暴露必要的业务上下文
- 保留与补齐关键安全、审计、异步日志
- 通过 OTel agent / SDK 发出 traces / metrics
- 在容器化运行路径下，把结构化日志输出到 stdout，交由 collector 的日志 receiver 统一接入

gateway collector 负责：

- 补充统一资源属性
- 批量、重试、memory limiter、backpressure 防护
- 脱敏和字段治理
- trace 采样
- 通过 OTLP receiver 接收 traces / metrics
- 通过日志 receiver 接收容器 stdout 日志
- 把处理后的 signals 导出到 Elastic

Elastic 负责：

- logs / metrics / traces 的统一存储和检索
- Kibana 视图、Saved Search、Dashboard、Alerting

### 5.3 信号接入策略

为了控制第一阶段复杂度，本方案对不同 signal 采用不同的接入策略：

- `traces`
  - 第一阶段优先使用 OTel Java agent 自动采集 HTTP、JDBC、Kafka、HTTP client 等通用链路
- `metrics`
  - 第一阶段优先使用 OTel agent / runtime metrics 与现有应用指标并存
  - 现有 Prometheus 抓取的应用指标仍是 Phase 1 告警与服务健康看板的权威来源
  - OTel metrics 在 Phase 1 主要用于 Elastic 侧关联与探索，不作为第一批告警的唯一依据
- `logs`
  - 业务日志继续由应用输出结构化日志
  - Phase 1 明确采用“容器 stdout -> docker json log -> collector filelog receiver -> Elastic”的接入方式
  - 该路径复用当前仓库已有的容器日志采集思路，便于从现有 `Promtail` 方案迁移到 collector 方案
  - OTLP logs 作为后续优化项保留，不作为 Phase 1 前提

这意味着第一阶段不要求所有日志都改成“手工 SDK 发 OTLP 日志”，而是优先统一字段模型、上下文关联和 collector 入口。

### 5.4 Trace Header Bridge 规则

Phase 1 对 `traceparent` 与 `X-Trace-Id` 的桥接规则统一如下：

1. 若入站请求存在合法 `traceparent`，则其携带的 trace id 是标准 `trace.id` 的唯一权威来源。
2. 若 `traceparent` 缺失或非法，但 `X-Trace-Id` 可以被规范化为合法 32 位小写 hex，则使用该值作为标准 `trace.id`，并向下游补齐对应 `traceparent`。
3. 若二者都缺失或非法，则生成新的标准 `trace.id`。
4. 响应头继续回写 `X-Trace-Id`，其值始终等于最终采用的标准 `trace.id`。
5. 非法 `X-Trace-Id` 不得直接进入标准 trace 字段；如需排查兼容问题，仅允许在边界层以布尔型或受控调试字段标记“legacy trace header invalid”，不记录原始值。

这条规则的目标是：

- 优先遵守 W3C Trace Context
- 不打破现有 `X-Trace-Id` 使用习惯
- 避免不同服务分别决定“谁是主 trace id”

### 5.5 本地与生产的统一性

在启用 observability profile 的容器化运行路径下，本地与生产保持同构链路：

- traces / metrics 都经过 `OTel -> EDOT gateway collector -> Elastic`
- logs 都经过 `stdout -> container json logs -> collector filelog receiver -> Elastic`
- 都使用相同字段模型
- 差异仅体现在输出格式和规模：
  - 生产环境：JSON 日志
  - 开发阶段的直接本地运行（如 `mvn spring-boot:run`）：允许保留可读文本日志，但该路径不属于 Phase 1 的 ingestion 验收范围

这避免后续出现“本地 grep 思维，生产 Kibana 思维”的割裂，同时不强行要求所有非容器本地运行场景在第一阶段也完成同样的采集接入。

---

## 6. 字段与事件模型

### 6.1 设计原则

字段设计遵循：

1. 优先使用 OpenTelemetry resource/span attributes 与 Elastic Common Schema（ECS）
2. 业务自定义字段统一收敛到 `community.*` 命名空间
3. 结构化字段是检索、聚合和告警的依据，自由文本 `message` 只作为补充
4. 不长期依赖手写字符串前缀作为主分类手段

### 6.2 必备基础字段

所有关键日志至少应包含：

- `@timestamp`
- `service.name`
- `service.version`
- `deployment.environment`
- `log.level`
- `message`
- `trace.id`
- `span.id`（若当前上下文存在）
- `community.category`
- `community.action`
- `community.outcome`

建议补充的资源字段包括：

- `service.namespace=community`
- `host.name`
- `container.id`
- `process.pid`

`service.version` 的来源在本方案中固定为：

- 容器化部署：优先使用镜像 tag 或构建时注入的 git SHA
- 本地 Maven 运行：默认使用模块 `pom.xml` 中的项目版本
- 若上述信息均不可用，可临时回退为 `unknown`，但不作为生产验收合格状态

### 6.3 事件分类

统一采用以下类别：

- `community.category=access`
  - HTTP / WS 入口访问与连接生命周期
- `community.category=audit`
  - 有审计意义的写操作和敏感动作
- `community.category=security`
  - 登录、登出、refresh、限流、验证码、鉴权拒绝、策略命中
- `community.category=business`
  - 关键业务状态变化
- `community.category=integration`
  - 跨服务、跨模块、跨系统调用
- `community.category=async`
  - Kafka、outbox、xxl-job、异步补偿、批处理
- `community.category=exception`
  - 未处理异常、数据访问异常、降级/退化

### 6.4 outcome 取值约束

`community.outcome` 仅允许以下固定值：

- `success`
- `failure`
- `denied`
- `skipped`
- `degraded`
- `retry`
- `dead`

禁止使用随意的近义词，如 `ok`、`passed`、`error`、`done`、`partial-success`。

### 6.5 常见字段补充

HTTP / gateway 事件：

- `http.request.method`
- `url.path`
- `http.response.status_code`
- `event.duration`

审计事件：

- `user.id`
- `community.target_type`
- `community.target_id`

异步事件：

- `community.topic`
- `community.event_id`
- `community.job_id`
- `community.retry_count`

任务 / 批处理：

- `community.batch_size`
- `community.indexed_count`

安全事件：

- `source.ip`
- `community.reason_code`
- `community.policy`

### 6.6 敏感字段约束

明确禁止记录以下内容的明文：

- 密码
- access token
- refresh token
- 验证码
- Cookie 明文
- password reset link
- 请求体或响应体中的敏感字段
- 用户上传文件内容

邮箱、手机号、用户名等标识原则上采用“非必须不全量记录”的策略。

敏感信息治理的责任边界明确如下：

- 应用侧“禁止输出敏感字段”是主保证
- collector 侧的脱敏 / 清洗只作为 defense-in-depth，不应被视为可以容忍应用先输出敏感数据的理由

---

## 7. 服务级落地方案

### 7.1 `community-gateway`

目标：

- 作为全链路入口起点
- 输出访问、拒绝、路由与降级事件
- 负责兼容现有 `X-Trace-Id` 机制

接入方式：

- 使用 OTel Java agent 自动采集入站 HTTP 与出站转发 HTTP
- 保留现有访问日志，但升级为结构化输出

第一阶段必须补的事件：

- `access`
  - method / path / status / duration / trace.id
- `integration`
  - 路由失败、后端不可达
- `security` 或 `integration`
  - fail-open / degrade 决策
- `access`
  - WS 握手、拒绝、断开摘要

不建议记录：

- 每次正常 HTTP 转发过程的细碎步骤
- 高频正常 WS 心跳

### 7.2 `community-app`

目标：

- 作为主业务单体，承载绝大多数审计和关键业务状态变化事件

接入方式：

- 使用 OTel Java agent 自动采集 servlet、jdbc、redis、http client、es client
- 保留现有 audit / exception 日志，并升级为统一结构化事件

说明：

- JDBC、HTTP client、Kafka 等通用链路直接依赖 Java agent 自动采集
- Redis / Elasticsearch 的自动采集以仓库中实际使用的客户端库和版本为准；若 agent 覆盖不足，再在 implementation plan 中决定是否补充定向 instrumentation

第一阶段必须补的事件：

1. 认证 / 安全：
- 登录成功
- 登录拒绝
- 触发验证码要求
- 验证码失败
- refresh 成功 / 失效
- logout
- 注册验证码签发 / 重发 / 验证
- 密码重置申请 / 确认

2. 内容写路径：
- 发帖、编辑、删除、置顶、加精
- 评论创建 / 删除
- 举报提交
- 审核动作

3. 异步与任务：
- 搜索重建开始 / 结束 / 跳过 / 失败
- outbox poll 摘要
- outbox retry / dead / no-handler
- xxl-job start / finish / fail

4. 用户敏感操作：
- 头像 upload token 申请
- 头像上传
- 头像切换
- 管理员角色变更

不建议记录：

- 普通 GET 成功
- 每个 DAO / Mapper 成功
- 同一操作在 controller 与 service 重复记录
- 原始请求体与响应体

### 7.3 `im-core`

目标：

- 承载 Kafka 消费、持久化、DLQ、内部 HTTP 的观测

接入方式：

- 使用 OTel Java agent 自动采集 servlet、jdbc、kafka producer/consumer
- 补齐 trace 与日志输出关联能力，不再只停留在 ThreadLocal

第一阶段必须补的事件：

- command 消费摘要
- retry / DLQ / no-handler
- 数据访问失败
- 未处理异常

不建议记录：

- 每条消息成功持久化都打 `info`

### 7.4 `im-realtime`

目标：

- 承载 WebSocket 会话、消息下发与 fanout 相关观测

接入方式：

- OTel agent 覆盖 HTTP / Kafka
- WebSocket 生命周期和关键 fanout 场景补少量手工业务事件

第一阶段必须补的事件：

- WS connect / auth fail / disconnect
- room bootstrap fail
- 治理校验 deny
- kafka send fail
- fanout flush fail

不建议记录：

- 每次正常 send ack 的全量 `info`
- 高频 room fanout 成功明细

---

## 8. Collector 与 Elastic 设计

### 8.1 Collector 拓扑

第一阶段采用单 `EDOT Collector` gateway：

```text
apps -> OTLP / filelog -> observability-gateway-edot-collector -> Elastic
```

后续如需扩容，可演进为：

```text
apps -> local collector(optional) -> gateway collector -> Elastic
```

对于本仓库目标覆盖的本地 `docker compose` 与未来 self-managed / ECK / ECE 类部署，Phase 1 的后端 ingress 选型固定为：

- 使用 `EDOT Collector` 的 gateway mode 作为统一接入层
- 不将“应用或边缘 collector 直接发送到 APM Server 的 OpenTelemetry intake”作为 Phase 1 正式方案
- 若未来迁移到 Elastic Cloud Hosted / Serverless，可再评估是否切换到 Managed OTLP Endpoint

### 8.2 Collector 责任边界

Collector 负责：

- 统一资源属性补全
- batch / retry / memory limiter
- 脱敏与字段清洗
- trace 采样
- 日志 receiver 对接容器 stdout 日志文件
- 导出到 Elastic

Collector 不负责：

- 从自由文本里推断业务语义
- 复杂业务分类
- 代替应用输出应有的结构化业务字段

### 8.3 为什么不把 Logstash 作为核心

在 `OpenTelemetry + Elastic` 路线下，gateway collector 已承担主要 ingest 职责。第一阶段若再引入 Logstash 作为核心主链路，会形成两套 ingest 逻辑并增加维护成本。

因此：

- 第一阶段不将 Logstash 作为核心组件
- 只有在后续需要接大量非 OTLP / 非标准数据源时，再考虑引入 Logstash 做兼容入口

### 8.4 Elastic 侧组织原则

本方案不依赖随意自定义散乱 index，而是强调统一 schema 与稳定字段。Kibana 至少要能按以下维度稳定过滤与聚合：

- `service.name`
- `deployment.environment`
- `community.category`
- `community.action`
- `community.outcome`
- `trace.id`
- `user.id`
- `community.job_id`
- `community.event_id`

Phase 1 不要求同时完成 metrics 权威来源切换：

- Prometheus / actuator 指标仍用于当前服务健康和基础告警
- Elastic 中的 metrics 主要用于统一关联、探索和后续迁移准备

---

## 9. Kibana 消费与告警

### 9.1 最低交付视图

至少交付以下视图：

1. 按 `trace.id` 排障
2. 按 `service.name` 看健康与错误
3. 按 `community.category / action / outcome` 看业务事件
4. 按 `community.job_id / event_id / topic` 看异步任务

### 9.2 第一批告警

建议第一批告警包括：

- 仍由 Prometheus 负责的服务可用性与基础健康告警
- `community.category=exception` 短时间激增
- `community.category=security and community.action=login and community.outcome=denied`
- `community.category=async and community.outcome=dead`
- `community.category=async and community.outcome=retry` 持续升高
- 搜索重建失败
- integration degraded 持续出现
- fail-open / origin guard degrade 事件出现

### 9.3 告警与日志的关系

长期原则：

- 核心告警优先基于 metrics
- logs 用于提供上下文与还原细节

第一阶段允许部分规则先基于结构化日志实现，但不应把“日志关键字检索”当成长期监控主方案。

---

## 10. 实施分期

### Phase 0：规范先行

交付物：

- 日志与观测规范文档
- 目标架构与迁移说明
- 字段字典、敏感字段规则、日志级别规则

### Phase 1：基础链路接通

交付物：

- `deploy/` 下新增 Elastic observability profile
- gateway 模式的 `EDOT Collector`
- 后端四服务的 OTel 运行参数接入
- 容器 stdout 日志进入 collector 的 filelog 接入
- 本地 compose 同构链路

### Phase 2：统一日志输出

交付物：

- 后端统一 JSON / text 双格式日志方案
- `community-app` 与 `im-core` 的 trace / log correlation 对齐
- 现有 access / audit / exception 日志升级为结构化事件
- `service.version` 注入来源在 compose / 本地运行中落地

### Phase 3：关键业务事件补齐

交付物：

- 认证链路日志
- 内容写路径日志
- 搜索 / outbox / 任务日志
- 用户敏感操作日志
- IM 关键失败面日志

### Phase 4：消费、告警与治理

交付物：

- Kibana saved views / dashboards
- 基础 alert rules
- 运行手册
- PR review checklist 中的可观测性要求

---

## 11. 验收标准

### 11.1 采集链路

- 四个核心服务都能在 Kibana 中按 `service.name` 查到日志
- 四个核心服务都有基础 traces
- 至少一条跨服务请求可以按 `trace.id` 串联
- collector 不出现持续导出失败、背压或显著丢数
- 在 observability compose profile 下，容器 stdout 日志能够通过 collector 进入 Elastic，而不依赖 Promtail

### 11.2 字段模型

- 关键日志都带 `service.name`、`trace.id`、`community.category`、`community.action`、`community.outcome`
- 关键日志的 `service.version` 来源可解释且在 compose 环境中稳定
- HTTP 访问事件带 method / path / status / duration
- 审计事件带 actor / target / action / outcome
- 异步事件至少带 `job_id / event_id / topic` 之一

### 11.3 业务场景

至少验证以下场景：

- 登录成功
- 登录失败并触发验证码
- refresh 失效
- 注册验证码签发
- 帖子创建成功
- 帖子删除
- 搜索重建成功 / 跳过 / 失败
- outbox retry / dead
- IM kafka send fail / room bootstrap fail

### 11.4 运维消费

- 存在可直接使用的 Kibana 查询视图
- 存在第一批告警规则
- 存在按 trace / user / async 任务排障的文档说明
- 团队约束了敏感字段与日志级别策略

---

## 12. 主要风险与控制措施

### 12.1 字段漂移

风险：

- 各模块自行发明字段名，导致查询碎片化

控制：

- 在规范文档中固定字段字典
- PR review 按字段字典收口

### 12.2 高频成功日志泛滥

风险：

- IM / Kafka / WS / 批处理产生过量 `info`

控制：

- 高频成功事件默认 `debug`
- `info` 只用于关键状态变化与摘要

### 12.3 把日志当监控替代品

风险：

- 全部依赖日志关键字告警，后续维护脆弱

控制：

- 第一阶段允许少量日志规则
- 长期目标逐步迁移到 metrics 告警

### 12.4 指标双写期的口径冲突

风险：

- Prometheus 指标与 Elastic 指标并存时，Dashboard 和告警可能使用了不同口径

控制：

- Phase 1 明确 Prometheus 是基础健康告警的权威来源
- Elastic metrics 主要用于关联分析，待后续显式切换后再承担主告警职责

### 12.5 trace 与旧 traceId 双轨混乱

风险：

- 排障主键不清晰

控制：

- 兼容期保留 `X-Trace-Id`
- 文档中明确标准主键是 `trace.id`
- 通过统一 bridge 规则定义 `traceparent` 与 `X-Trace-Id` 的优先级和回写行为

### 12.6 项目范围失控

风险：

- 同时推进后端、前端、infra、APM、全量日志重构

控制：

- 严格按 phase 推进
- 第一阶段仅覆盖后端服务链路

---

## 13. 最终文档与实施产物

本设计通过后，后续实施阶段至少需要产出：

1. `docs/OBSERVABILITY.md` 的迁移版说明
2. 一份独立的日志字段与治理规范
3. `deploy/` 下的 collector / elastic / kibana 配置
4. 后端统一日志配置与服务级接入说明
5. Kibana 查询模板与告警规则说明
6. 对应 implementation plan

---

## 14. 推荐结论

本仓库的后端可观测性体系推荐演进为：

- 采集标准：`OpenTelemetry`
- 后端平台：`Elastic Observability`
- 接入策略：先 `OTel Java agent + 结构化 JSON 日志`，再逐步补手工业务 span 和更细粒度事件
- 拓扑策略：先单 gateway collector，再按需要扩展
- 实施策略：先统一 schema 与基础链路，再补关键业务事件与消费层

这条路线比“只补日志”更完整，比“一次性全面平台迁移”更可控，也最符合当前仓库的结构与用户已经确认的目标。
