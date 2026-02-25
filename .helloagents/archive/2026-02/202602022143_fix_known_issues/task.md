# Task List: 现有功能问题修复与一致性加固（fix_known_issues）

Directory: `.helloagents/archive/2026-02/202602022143_fix_known_issues/`

---

## 1. 内容渲染契约（content-service / frontend）
- [√] 1.1 新增“HTML entity 解码”公共工具（仅白名单解码，避免过度解码），验证 why.md#requirement-内容渲染一致性与安全content-rendering-scenario-历史数据不再出现二次转义legacy-unescape
- [√] 1.2 content-service：在帖子/评论对外响应组装阶段对历史数据做一次性解码（配置开关控制，默认开启兼容），验证 why.md#requirement-内容渲染一致性与安全content-rendering-scenario-历史数据不再出现二次转义legacy-unescape
- [√] 1.3 content-service：停止“帖子写入阶段”`HtmlUtils.htmlEscape`（保留敏感词过滤与输入校验），验证 why.md#requirement-内容渲染一致性与安全content-rendering-scenario-新增内容按存储原文展示端安全渲染store-raw-render-safe
- [√] 1.4 content-service：停止“评论写入阶段”`HtmlUtils.htmlEscape`（保留敏感词过滤与输入校验），验证 why.md#requirement-内容渲染一致性与安全content-rendering-scenario-新增内容按存储原文展示端安全渲染store-raw-render-safe
- [√] 1.5 frontend：审计并收敛 `v-html` 点位（仅允许受控渲染：Markdown 组件/`<em>` 高亮），验证 why.md#requirement-内容渲染一致性与安全content-rendering-scenario-xss-注入不会执行xss-safe
- [√] 1.6 可选：提供一次性“数据修复 + reindex”运维方案（执行前备份，执行后重建索引），验证 why.md#requirement-内容渲染一致性与安全content-rendering-scenario-历史数据不再出现二次转义legacy-unescape

## 2. 可信代理 IP（gateway / common）
- [√] 2.1 gateway：增加启动校验（trusted-proxy enabled 但 cidrs 为空/非法时 fail-fast 或显式告警），验证 why.md#requirement-可信代理-ip-解析正确trusted-proxy-ip-scenario-反代ingress-部署下限流与统计按真实客户端-ipreal-ip
- [√] 2.2 common：对 servlet 侧 `ClientIpResolver` 的同类配置补齐一致性校验与文档（避免“网关正确但服务侧错误”），验证 why.md#requirement-可信代理-ip-解析正确trusted-proxy-ip-scenario-反代ingress-部署下限流与统计按真实客户端-ipreal-ip
- [√] 2.3 更新 `deploy/.env.example`：提供 trusted-proxy 生产示例（CIDR allowlist），验证 why.md#requirement-可信代理-ip-解析正确trusted-proxy-ip-scenario-反代ingress-部署下限流与统计按真实客户端-ipreal-ip
- [√] 2.4 更新 `docs/SECURITY.md`：补齐 trusted-proxy 配置注意事项与安全边界说明，验证 why.md#requirement-可信代理-ip-解析正确trusted-proxy-ip-scenario-反代ingress-部署下限流与统计按真实客户端-ipreal-ip

## 3. 网关 analytics 采集（gateway）
- [√] 3.1 gateway：新增有界队列/事件总线组件（包含队列长度/丢弃数指标），验证 why.md#requirement-网关采集链路治理gateway-analytics-collect-scenario-采集失败不影响主链路且可观测isolation-observable
- [√] 3.2 gateway：改造 `AnalyticsCollectGlobalFilter` 仅负责采集字段并投递事件（不直接 `subscribe()` 执行业务调用），验证 why.md#requirement-网关采集链路治理gateway-analytics-collect-scenario-采集失败不影响主链路且可观测isolation-observable
- [√] 3.3 gateway：新增异步 worker 消费队列并调用 analytics-service（超时/并发上限/失败指标/日志），验证 why.md#requirement-网关采集链路治理gateway-analytics-collect-scenario-采集失败不影响主链路且可观测isolation-observable

## 4. internal client 收敛（auth-service / common）
- [√] 4.1 common：梳理 `InternalClientSupport` 的复用点（headers/错误透传/超时识别/metrics），补齐缺口（如需），验证 why.md#requirement-internal-client-统一与可观测internal-client-scenario-internal-token-配置错误时提示一致consistent-error
- [√] 4.2 auth-service：重构 `UserServiceInternalClient` 复用 `InternalClientSupport`（含 pass-through error handler 与统一 unwrap），验证 why.md#requirement-internal-client-统一与可观测internal-client-scenario-internal-token-配置错误时提示一致consistent-error
- [-] 4.3 可选：按相同模式收敛其他 internal client（逐个服务渐进式迁移，避免大爆炸改动），验证 why.md#requirement-internal-client-统一与可观测internal-client-scenario-internal-token-配置错误时提示一致consistent-error

## 5. 最终一致 UX（frontend）
- [√] 5.1 frontend：发帖成功后提示“搜索/通知可能延迟”，并提供“立即查看帖子”入口，验证 why.md#requirement-最终一致体验补足eventual-consistency-ux-scenario-发帖编辑后搜索延迟提示与快速恢复search-lag-hint
- [√] 5.2 frontend：编辑帖子成功后提示“搜索结果更新可能延迟”，并提供“刷新搜索/返回详情”等快捷动作，验证 why.md#requirement-最终一致体验补足eventual-consistency-ux-scenario-发帖编辑后搜索延迟提示与快速恢复search-lag-hint

## 6. Security Check
- [√] 6.1 执行安全检查（按 G9：输入校验、敏感信息、权限控制、trusted-proxy 与 internal-token 默认安全态），并运行 `scripts/security-check.sh`

## 7. Documentation Update（Knowledge Base）
- [√] 7.1 更新模块文档：`.helloagents/modules/content.md`（渲染契约/兼容开关/迁移建议）
- [√] 7.2 更新模块文档：`.helloagents/modules/frontend.md`（受控渲染点位/最终一致提示）
- [√] 7.3 更新模块文档：`.helloagents/modules/gateway.md`（trusted-proxy/analytics 采集队列）
- [√] 7.4 更新模块文档：`.helloagents/modules/common.md`（internal client 支撑/HTML entity 工具）
- [√] 7.5 更新模块文档：`.helloagents/modules/auth-service.md`（internal client 改造点/错误语义）
- [√] 7.6 更新运维/排障文档（如需）：补齐 trusted-proxy 与 analytics 采集排障要点（`.helloagents/modules/runbooks/*`）

## 8. Testing
- [√] 8.1 content-service：追加渲染回归用例（特殊字符/Markdown/XSS），覆盖“历史解码开关”与“停止写入 escape”
- [√] 8.2 gateway：追加 trusted-proxy 校验与 analytics 队列降级用例（采集失败不影响主链路）
- [√] 8.3 auth-service：追加 internal client 错误映射与 forbidden 场景用例
- [-] 8.4 回归执行：运行 `scripts/smoke-i0-auth.sh`，必要时补齐新的 smoke 脚本并在 `scripts/doctor.sh` 中挂载（当前未启动本地 gateway：`localhost:12882` 不可达）
