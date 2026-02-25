# Change Proposal: 现有功能问题修复与一致性加固（fix_known_issues）

## Requirement Background
当前代码已具备“社区”核心链路（登录/发帖/评论/点赞关注/私信通知/搜索/统计），但在若干关键实现上存在可感知缺陷与潜在风险，主要体现在：

1. **内容渲染存在二次转义**：后端写入时对帖子/评论做了 HTML escape，前端 Markdown 渲染又再次 escape 并通过 `v-html` 输出，容易出现 `&amp;lt;` 等“看得见的转义符”，影响阅读体验。
2. **代理/Ingress 场景下客户端 IP 可能失真**：网关与服务端默认不信任 `X-Forwarded-For`，若生产部署在反向代理后且未正确配置可信代理 CIDR，将直接影响限流、登录风控、UV 统计等效果（误伤/失效）。
3. **网关 WebFlux 链路存在“脱链副作用”实现**：网关采集在过滤器里直接 `subscribe()` 触发异步调用，容易带来背压/上下文/可观测性一致性问题，且排障复杂度上升。
4. **internal HTTP client 能力重复实现**：项目已有统一的 internal client 支撑能力，但部分服务仍自行实现一套，长期会导致超时策略、错误语义、指标标签不一致，维护成本上升。
5. **最终一致体验缺口仍可能被用户感知**：发帖/编辑后搜索与通知依赖异步链路（Kafka/ES/消费）更新，虽具备 outbox、幂等等可靠性措施，但产品层需要更明确的提示/引导，降低“误以为功能坏了”的概率。

本变更以“修复已知问题 + 保持安全默认态 + 降低维护成本”为目标，提供一套可落地的改造方案与执行任务清单。

## Change Content
1. 统一“内容存储/展示”契约：避免二次转义，同时保持 XSS 安全与历史数据兼容。
2. 补齐可信代理（trusted proxy）配置与校验策略：让限流/风控/统计在真实部署形态下可靠生效。
3. 重构网关 analytics 采集触发方式：避免在 filter 内直接 `subscribe()`，提升可观测性与可靠性。
4. 收敛 internal client：统一 header/错误映射/指标记录策略，减少跨服务漂移。
5. 增强最终一致的 UX 提示与操作引导：让“延迟是预期行为”可解释、可恢复、可自助排障。

## Impact Scope
- **Modules:** `frontend/`, `gateway/`, `content-service/`, `auth-service/`, `common/`（可能扩展到 `search-service/`）
- **Files:** 预计涉及网关 filter、安全配置、content 写入/读取链路、前端渲染组件与相关页面、internal client 代码与文档
- **APIs:** 以“不破坏现有 API 形状”为优先；如需增加兼容字段（例如内容编码标记/调试字段），必须保持向后兼容
- **Data:** 可能涉及历史帖子/评论内容的“展示侧解码/一次性数据修复”，以及搜索索引重建

## Core Scenarios

### Requirement: 内容渲染一致性与安全（content-rendering）
**Module:** `content-service` / `frontend`（展示侧）/ `search-service`（高亮展示侧）

统一约定：**服务端返回内容为“原始文本（text）语义”，展示端负责转义与 Markdown 渲染（白名单标签）**。同时需要兼容历史数据（已 escape 的内容）。

#### Scenario: 历史数据不再出现二次转义（legacy-unescape）
在不做全量数据迁移的前提下，历史帖子/评论展示不再出现 `&amp;lt;` 等现象，用户看到的应是“原始字符”或“安全渲染后的 Markdown”。

#### Scenario: 新增内容按“存储原文，展示端安全渲染”（store-raw-render-safe）
用户新发帖/新评论可输入包含 `& < >`、代码块、链接等内容；页面展示符合预期（Markdown 可用、文本可读、格式不被破坏）。

#### Scenario: XSS 注入不会执行（xss-safe）
用户输入如 `<script>alert(1)</script>` 不会被执行，展示侧应以安全文本形式呈现；同时不引入新的 `v-html` 注入面。

### Requirement: 可信代理 IP 解析正确（trusted-proxy-ip）
**Module:** `gateway` / `common`（服务侧）

#### Scenario: 反代/Ingress 部署下限流与统计按真实客户端 IP（real-ip）
当网关/服务部署在 Nginx/Ingress/Load Balancer 后时：
- 如果 remoteAddr ∈ 可信代理 CIDR，则读取 `X-Forwarded-For` 的第一个 IP 作为客户端 IP；
- 否则严格使用 remoteAddr（避免 XFF 伪造）。

### Requirement: 网关采集链路治理（gateway-analytics-collect）
**Module:** `gateway` / `analytics-service`

#### Scenario: 采集失败不影响主链路且可观测（isolation-observable）
analytics-service 不可用/超时/内部鉴权失败时：
- 主请求转发不受影响（仍返回原业务响应）；
- 采集失败应有可观测信号（metrics/log），便于排障；
- 避免在 filter 内直接 `subscribe()` 引入“脱链”副作用的隐性成本。

### Requirement: internal client 统一与可观测（internal-client）
**Module:** `auth-service` / `common`

#### Scenario: internal-token 配置错误时提示一致（consistent-error）
当 internal-token 配置缺失或错误时：
- 调用方收到一致的错误语义（包含清晰提示与 traceId 线索）；
- 指标维度/标签统一，便于跨服务聚合分析。

### Requirement: 最终一致体验补足（eventual-consistency-ux）
**Module:** `frontend` / `search-service` / `content-service`

#### Scenario: 发帖/编辑后搜索延迟提示与快速恢复（search-lag-hint）
用户发帖/编辑后立即去搜索：
- 若短时搜索不到，页面给出明确说明（最终一致窗口）；
- 给出低成本自助动作（刷新/重试/跳转到帖子详情等）；
- 管理员侧保留可控的 reindex/排障入口与引导。

## Risk Assessment
- **Risk: 信任代理配置不当导致 IP 伪造/风控失效**
  - **Mitigation:** 保持 fail-closed 默认；只在 remoteAddr 命中可信 CIDR 时才解析 XFF；对“enabled=true 且 cidrs 为空”等危险配置做启动校验。
- **Risk: 内容渲染策略变更导致历史数据展示异常**
  - **Mitigation:** 采用“展示侧一次性解码/兼容开关”方案；配合灰度（先兼容读，再逐步停止写入 escape；最终可选做数据修复与索引重建）。
- **Risk: 网关采集异步化引入内存队列堆积**
  - **Mitigation:** 队列有界 + 丢弃策略 + 指标监控（队列长度/丢弃数/失败率）；超时与并发上限控制。

