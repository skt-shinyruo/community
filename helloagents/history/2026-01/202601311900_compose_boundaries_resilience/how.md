# Technical Design: 微服务边界与韧性治理（Docker Compose 部署）

## Technical Solution

### Core Technologies
- 部署：Docker Compose（现有 `deploy/docker-compose.yml` + 端口覆盖文件）
- 网关：Spring Cloud Gateway（north-south 统一入口）
- 服务：Spring Boot 3（Servlet Web 为主；gateway 为 Reactive）
- 数据：MySQL（同实例多 schema）、Redis、Kafka、Elasticsearch
- 可观测：Prometheus / Grafana / Loki / Promtail（已存在）
- 可选 Tracing：Micrometer Tracing + OTLP（docker compose 额外起 tracing 后端，如 Tempo/Jaeger/Zipkin 其一）

### Implementation Key Points
1. **边界三分统一（命名 + 策略）**
   - 对外：`/api/**`
   - 运维：`/api/ops/**`（强保护）
   - 对内：`/internal/**`（仅服务间调用）
   - 旧路径（含 internal 字样的对外入口）短期兼容并明确弃用窗口。
2. **Compose 网络隔离（不依赖 mesh）**
   - 仅 `gateway` 与 `frontend` 通过 `ports` 暴露到宿主机；其他服务仅加入内部网络，不做端口映射。
   - 观测组件可按需暴露 `grafana/prometheus` 等端口；对生产建议只在内网暴露或加认证。
3. **internal/ops 入口的“默认拒绝”**
   - `/internal/**`：依赖网络不暴露 + `X-Internal-Token` fail-closed（token 未配置即拒绝）。
   - `/api/ops/**`：在 gateway 层以角色（ADMIN）+ `X-Ops-Token` + allowlist/频率限制保护。
4. **DB 最小权限（同实例、多账号）**
   - 每个 schema 绑定独立用户（content/message/search/social/user 等），各服务 datasource 只使用本域账号。
   - 保留 root/全局账号仅用于初始化与备份，不用于业务运行。
5. **同步调用韧性：以“统一 client 基线库 + 明确降级边界”落地**
   - 统一 RestTemplate/WebClient：connect/read timeout、traceId 透传、错误语义保真（下游 `Result.code/message/traceId`）。
   - 明确：鉴权/内部写接口 fail-closed；聚合读接口允许 degrade 并记录指标。
   - 可选增强：引入 Resilience4j（只对关键链路启用 bulkhead/circuit breaker/retry），避免一次性全量引入。
6. **异步一致性：默认可靠 + 可运营**
   - 生产侧 outbox 默认开启（relay job + backlog 指标）。
   - 消费侧统一 DefaultErrorHandler（有限重试 + DLQ）与幂等点位；补齐 DLQ 回放脚本与 runbook。
7. **契约与可观测性体系化**
   - DTO 白名单契约测试：固化“对外不暴露治理字段”的边界。
   - Tracing（可选）：通过 OTLP 导出到 docker compose 内的 tracing 后端；HTTP/Kafka 链路可串联。

## Architecture Design
```mermaid
flowchart TD
  Browser[Browser] --> FE[frontend]
  FE --> GW[gateway (/api/**,/api/ops/**)]

  subgraph ComposeInternal[Docker Compose internal network]
    GW --> Auth[auth-service]
    GW --> User[user-service]
    GW --> Content[content-service]
    GW --> Social[social-service]
    GW --> Msg[message-service]
    GW --> Search[search-service]
    GW --> Ana[analytics-service]

    Content -->|Kafka publish| Kafka[(Kafka)]
    Social -->|Kafka publish| Kafka
    User -->|Kafka publish| Kafka
    Kafka -->|consume| Search
    Kafka -->|consume| Msg
    Kafka -->|consume| Content
  end

  subgraph Storage[Storage]
    MySQL[(MySQL: multi-schema + per-service user)]
    Redis[(Redis)]
    ES[(Elasticsearch)]
  end

  Auth --> MySQL
  User --> MySQL
  Content --> MySQL
  Social --> MySQL
  Msg --> MySQL
  Search --> MySQL
  Ana --> Redis
  Search --> ES
```

## Architecture Decision ADR

### ADR-001: 不使用 Kubernetes/service mesh，采用 Compose + 应用侧治理
**Context:** 部署环境明确不具备/不采用 k8s + mesh。  
**Decision:** 通过 Docker Compose 的网络隔离 + gateway 策略 + common 基线库在应用层落地边界与韧性治理。  
**Rationale:** 满足现有部署约束，同时把关键风险（误暴露/级联故障/一致性缺口）用可落地的机制收敛。  
**Alternatives:** service mesh：拒绝原因：不符合部署约束。  
**Impact:** 需要更强调默认 fail-closed、配置一致性与回归测试（避免漂移）。

### ADR-002: 运维入口使用 `/api/ops/**`，禁止对外 internal 命名
**Context:** “对外 internal 命名”易导致边界误判与授权复杂度上升。  
**Decision:** 对外运维统一 `/api/ops/**`；`/internal/**` 保持服务间调用专用并默认不暴露。  
**Impact:** 需要短期兼容与弃用窗口；前端与调用方同步迁移。

### ADR-003: DB 权限隔离作为边界硬约束
**Context:** 同一 MySQL 实例多 schema 容易被越界访问侵蚀边界。  
**Decision:** 按 schema 建立 per-service DB user 并授予最小权限；业务服务不得使用 root/全局账号。  
**Impact:** 初始化脚本与配置需要调整；越界访问会在早期暴露并被修复。

## API Design

### POST /api/ops/search/reindex
- **用途：** 对外运维入口（管理员触发重建索引）
- **鉴权：** ADMIN 角色 + `X-Ops-Token` + allowlist/频率限制（默认拒绝）
- **实现：** gateway 转发到 search-service 的 `/internal/search/reindex`（内部 token 由 gateway 或专用 ops 通道注入）

### POST /internal/search/reindex
- **用途：** 内部运维入口（仅服务间调用）
- **鉴权：** `X-Internal-Token` fail-closed + single-flight 锁（避免重复触发）

## Data Model
### MySQL per-service 最小权限
- `deploy/mysql-init/` 增加用户创建与授权脚本（或在现有脚本中补齐）
- nacos 配置与环境变量：各服务使用 `{SERVICE}_DB_USERNAME/{SERVICE}_DB_PASSWORD`，不再复用全局 `MYSQL_USER`

## Security and Performance
- **Security:**
  - Compose 不暴露 internal 服务端口到宿主机
  - internal-token 分域 + 轮转窗口（previous token）
  - ops-token（break-glass）默认关闭，启用需 allowlist + 频率/并发限制 + 审计
- **Performance:**
  - 同步调用：严格超时 + 统一错误语义避免“慢请求放大”
  - 聚合读接口：允许 degrade，避免级联阻塞

## Testing and Deployment
- **Testing:**
  - 权限矩阵回归：`/internal/**` 不可对外访问，`/api/ops/**` 强保护
  - DTO 契约测试：字段白名单
  - Kafka：unknown/version handling、DLQ/回放流程
- **Deployment:**
  - docker compose：新增/调整网络与端口暴露策略；补齐 `.env.example` 与 nacos-config 的一致性
  - 可选 tracing：增加 tracing 容器与 OTLP 导出配置（不影响默认启动）

