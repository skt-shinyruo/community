# Task List: Origin 白名单收敛到 gateway

Directory: `helloagents/plan/202601192246_origin_guard_gateway/`

---

## 1. gateway（统一入口：CORS + OriginGuard）
- [√] 1.1 新增 `OriginGuardGlobalFilter`：对 `POST /api/auth/login|refresh|logout` 执行 Origin allowlist 校验（缺失 Origin 放行；不在 allowlist 返回 403 JSON），verify why.md#requirement-前端端口漂移不再触发登录-403-Scenario-前端在-12888vite-dev--proxy登录
- [√] 1.2 gateway 配置：允许 `http://localhost:12888`，并确保 CORS allowlist 与 OriginGuard allowlist 复用同一份列表，verify why.md#requirement-保留对敏感接口的-origin-防护能力-Scenario-不在-allowlist-的-origin-访问敏感接口
- [√] 1.3 新增 gateway 单元测试：覆盖 allowlist 命中/未命中/缺失 Origin 三种情况（未命中应短路 403 且不触发下游），verify why.md#requirement-保留对敏感接口的-origin-防护能力-Scenario-不在-allowlist-的-origin-访问敏感接口

## 2. auth-service（移除 Origin 白名单强校验）
- [√] 2.1 移除 AuthService 中 `assertAllowedOrigin` 相关逻辑与配置依赖，verify why.md#requirement-前端端口漂移不再触发登录-403-Scenario-前端在-12888vite-dev--proxy登录
- [√] 2.2 移除 `security.jwt.allowed-origins` 配置字段（避免误导），并确保应用启动与现有测试不受影响

## 3. Security Check
- [√] 3.1 校验变更不会引入“放宽生产 CORS/Origin”风险：默认配置仍严格；确认只对敏感接口做 OriginGuard；确认 auth-service 仍假设内网-only（文档中说明）

## 4. Documentation Update
- [√] 4.1 更新 `docs/SECURITY.md`：说明 Origin 策略收敛到 gateway，auth-service 不再维护 allowlist
- [√] 4.2 更新 `helloagents/wiki/modules/gateway.md` 与 `helloagents/wiki/modules/frontend.md`：更新排查指引与配置入口（SSOT）

## 5. Testing
- [√] 5.1 运行 gateway 测试：`mvn -pl gateway -am test`
- [√] 5.2 运行 auth-service 测试：`mvn -pl auth-service -am test`
