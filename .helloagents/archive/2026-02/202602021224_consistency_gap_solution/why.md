# Change Proposal: 一致性缺口与依赖耦合治理（Perceived Consistency + RPC 收敛 + 幂等/配置护栏）

## Requirement Background

当前系统采用“同步 API + 异步事件”的混合架构：写路径落库/落 Redis 后发布 Kafka 事件，读侧通过各自的投影/索引提供体验（点赞投影、热度刷新、通知、搜索索引等）。

现状痛点主要集中在四类：

1. **最终一致的可见不一致与冷启动缺口**
   - 写成功后短时间内读不到/读旧值（如 Like 投影、搜索索引、通知生成等均依赖 Kafka 传播与消费）。
   - 冷启动或投影滞后/漏消费时，读侧可能出现“长期缺口”（例如投影未建基线时返回 0 或空）。
2. **同步 RPC 聚合导致延迟放大与级联失败**
   - 用户主页聚合依赖 social-service 多次同步调用，抖动时要么变慢要么降级为 0（对用户误导）。
   - 私信发送在 `toName` 场景需要回源解析用户名，增加写路径的依赖放大。
3. **幂等机制强约束但对非浏览器客户端不友好**
   - 部分写接口缺失 `Idempotency-Key` 直接 400，对脚本/第三方调用容易踩坑。
   - processing TTL 固定，慢链路存在“锁过期后重复执行”的理论风险。
4. **安全与配置项多，误配成本高**
   - JWT（HMAC secret）、internal/ops token、prod profile 等均属于“必须一致/必须显式”的配置；一旦漏配或不一致，排障成本较高。

## Change Content

1. **感知一致性（Perceived Consistency）优先**：用“Read-your-writes”策略降低用户可见不一致。
   - 对用户交互最敏感的链路（点赞/取消点赞、发帖/评论后立即查看）优先保证体验一致；
   - 不强行把最终一致改成强一致，而是提供可控的“前端覆盖/提示/自愈”策略。
2. **同步依赖收敛**：将 user 主页所需的 social 统计收敛为单次 internal 调用，降低 fan-out。
3. **幂等易用性与鲁棒性提升**：通过 TTL 配置化、错误提示与工具化脚本，让非浏览器客户端更容易正确调用。
4. **配置护栏**：提供可执行的配置自检与明确的“必须项清单”，减少误配上线风险。

## Impact Scope

- **Modules:** `frontend/`, `user-service/`, `social-service/`, `message-service/`, `common/`, `docs/`, `scripts/`
- **APIs:**
  - social-service internal read API（新增/扩展，供 user-service 聚合调用）
  - （可选）对外 API 增加“staleness/降级标记”字段（保持向后兼容）
- **Data:**
  - P0 不强制变更数据模型；P1 可选引入 DB-level idempotency（需要 schema 变更）

## Core Scenarios

### Requirement: Perceived Consistency (Read-your-writes)

#### Scenario: Like/Unlike 后立即看到一致的 likeCount 与 liked
- 前提：用户已登录，在帖子详情或列表中执行点赞/取消点赞。
- 期望：
  - 写接口响应返回最新 `liked/likeCount`，UI 立即更新；
  - 在短时间内刷新/重新拉取时仍能“尽量保持一致”（优先以 SSOT/覆盖策略保证体验），最终以投影收敛为准。

#### Scenario: 发帖/编辑后，搜索结果延迟可解释且可观测
- 前提：用户发帖/编辑后立刻在搜索页搜索关键词。
- 期望：
  - 明确提示“搜索索引最终一致，可能延迟数秒”；
  - 后端具备可观测指标（事件投递/消费滞后、DLQ、reindex/纠偏工具）。

### Requirement: RPC Aggregation Hardening (降低 fan-out)

#### Scenario: 用户主页不再串行多次调用 social-service
- 前提：访问用户主页（可能未登录/已登录）。
- 期望：
  - 将“获赞/关注/粉丝/关注状态”等信息收敛为 1 次 internal 调用；
  - 下游抖动时避免“误导性的 0”，至少可区分“未知/降级”。

#### Scenario: 私信发送使用 toName 时尽量不放大依赖
- 前提：用户在前端输入用户名发送私信。
- 期望：
  - 对同一用户名重复发送不应每次回源解析；
  - 下游不可用时错误语义明确（不伪装成参数错误）。

### Requirement: Idempotency UX & Safety (幂等体验与安全)

#### Scenario: 非浏览器客户端可轻松正确使用幂等
- 前提：脚本/第三方调用 `POST /api/posts`、`POST /api/posts/{id}/comments`、`POST /api/messages` 等写接口。
- 期望：
  - 提供明确的 header 规范与示例脚本；
  - server 侧 TTL 可配置，降低慢链路重复执行风险。

### Requirement: Config Guardrails (配置护栏)

#### Scenario: 生产部署缺少 prod profile 或关键密钥时可快速发现
- 期望：
  - 提供可执行自检（本地/CI/部署前）；
  - 关键配置缺失/不一致能在启动期或检查期被明确指出。

## Risk Assessment

- **Risk:** 引入/扩展 internal read API 增加攻击面。  
  **Mitigation:** 仅 internal 网络可达 + `X-Internal-Token` 兜底（必要时纳入 ops-guard），并补齐审计与限流策略。
- **Risk:** 感知一致性采用前端覆盖，可能出现多标签页/多终端短暂不一致。  
  **Mitigation:** 覆盖 TTL 短、可手动刷新、最终以投影收敛为准；对“强一致”诉求单独评估（避免全局强一致化）。
- **Risk:** 引入缓存（用户名解析/聚合结果）可能导致短暂陈旧。  
  **Mitigation:** TTL 受控 + 以“仅优化非关键读路径”为原则，写路径保持明确失败语义与可观测。
