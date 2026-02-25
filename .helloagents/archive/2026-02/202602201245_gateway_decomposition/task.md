# Task List: Gateway 边界收敛与职责拆分（降爆炸半径）

Directory: `.helloagents/archive/2026-02/202602201245_gateway_decomposition/`

---

## 1. gateway（边界收敛与透明化）
- [√] 1.1 重构 gateway 安全策略：拆分为 “ops/internal 严格链” + “默认透明链（permitAll）”，移除业务公开白名单与业务角色矩阵（默认路径），验证 why.md#requirement-r1-gateway-boundary-contract、why.md#requirement-r2-authorization-responsibility-shift-service-as-ssot
- [√] 1.2 增加安全模式开关（legacy-matrix vs transparent）：支持快速回滚；当前默认 transparent（降低误配爆炸半径），验证 why.md#scenario-r1s1-public-traffic-does-not-depend-on-gateway-allowlist
- [√] 1.3 补齐 gateway 安全契约测试：覆盖 transparent 模式下公开接口可达性、internal 显式拒绝、ops 角色校验，验证 why.md#scenario-r1s2-internal-paths-are-always-denied-at-gateway、why.md#scenario-r3s1-ops-changes-do-not-impact-main-gateway-forwarding
- [√] 1.4 将 gateway 内“业务相关 filter”（audit/analytics/业务限流规则）开关化 + 规则收敛（减少全站影响面），验证 why.md#requirement-r4-observability-offload-auditanalytics
- [√] 1.5 限流分层落地：gateway 只保留边界 anti-abuse（login/register/captcha/reset）与 ops 防误触；业务敏感限流迁移到服务侧或默认关闭，验证 why.md#requirement-r5-rate-limit-layering

## 2. 服务侧授权 SSOT（安全契约测试）
- [ ] 2.1 content-service：梳理公开读接口与管理接口的 401/403 行为，补齐安全契约测试（匿名可读/写需登录/管理需角色），验证 why.md#scenario-r2s1-business-authorization-moves-to-service-side
- [ ] 2.2 user-service：补齐 admin 接口与公开接口的安全契约测试（/api/users/admin/** 必须 ADMIN），验证 why.md#scenario-r2s1-business-authorization-moves-to-service-side
- [ ] 2.3 social-service：补齐公开计数接口与写接口的安全契约测试（点赞/关注写入必须登录），验证 why.md#scenario-r2s1-business-authorization-moves-to-service-side
- [√] 2.4 search-service：补齐公开搜索接口与内部接口（含 legacy 410 stub）的安全契约测试，验证 why.md#scenario-r2s1-business-authorization-moves-to-service-side
- [√] 2.5 analytics-service：补齐 analytics 管理接口角色校验测试（ADMIN/MODERATOR），验证 why.md#scenario-r2s1-business-authorization-moves-to-service-side
- [ ] 2.6 message-service：补齐消息写接口安全契约测试（必须登录），验证 why.md#scenario-r2s1-business-authorization-moves-to-service-side

## 3. 运维平面隔离（推荐：ops-service）
- [√] 3.1 新增 `ops-service` Maven module（Spring Boot 服务骨架 + Nacos/Dubbo/metrics 基础配置），验证 why.md#requirement-r3-ops-plane-isolation
- [√] 3.2 迁移 gateway 现有运维 handler 能力到 ops-service（reindex/outbox health & replay/likes backfill），并保持对外 API 不变：`/api/ops/**`，验证 why.md#scenario-r3s1-ops-changes-do-not-impact-main-gateway-forwarding
- [√] 3.3 gateway 路由切换：`/api/ops/** -> lb://ops-service`，移除对 `forward:/__gateway/**` 的依赖，验证 why.md#scenario-r3s1-ops-changes-do-not-impact-main-gateway-forwarding
- [√] 3.4 ops-service 安全：仅允许 ADMIN（gateway + ops-service 双保险）；并通过 gateway `blocked-path-patterns` 支持应急熔断，验证 why.md#scenario-r3s1-ops-changes-do-not-impact-main-gateway-forwarding
- [√] 3.5 更新部署与配置模板（docker-compose、prometheus、knowledge base/runbook），确保 ops-service 可独立发布与回滚

## 4. 可观测与统计旁路化（分阶段）
- [√] 4.1 短期：将 analytics 采集默认改为关闭并支持灰度开启；确保队列/并发/超时均有界且可观测（采集失败不影响主链路），验证 why.md#scenario-r4s1-analytics-is-best-effort-and-non-blocking
- [ ] 4.2 中期（推荐）：将 UV/DAU 采集改为事件驱动或日志驱动（gateway 不再通过 Dubbo 依赖 analytics-service），并为 analytics-service 增加消费端幂等/重试策略，验证 why.md#scenario-r4s1-analytics-is-best-effort-and-non-blocking
- [ ] 4.3 审计：明确“网关审计 vs 服务审计”的职责边界（写副作用审计优先在服务侧），并统一结构化日志字段（traceId/userId/costMs/outcome）

## 5. Security Check
- [?] 5.1 执行安全检查（按 G9：输入校验、敏感信息、权限控制、EHRB 风险规避）；重点关注“透明化后服务误放行”的门禁与回滚策略

## 6. Documentation Update
- [√] 6.1 更新 knowledge base：补充 gateway 边界契约、透明化模式说明、ops 平面隔离与回滚手册（`.helloagents/modules/gateway.md`、`.helloagents/arch.md`、相关 runbook）

## 7. Testing
- [?] 7.1 执行回归测试：已完成 `mvn test`（全模块）；smoke 脚本（按环境选择 `scripts/smoke-*.sh`）待在本地全依赖环境执行
