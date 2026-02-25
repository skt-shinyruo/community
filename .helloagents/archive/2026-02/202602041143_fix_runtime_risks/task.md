# Task List: 运行时风险点修复（Redis unionKey / Gateway 采集 / TraceId / OriginGuard / internal-ops）

Directory: `.helloagents/archive/2026-02/202602041143_fix_runtime_risks/`

---

## 1. analytics-service（Redis unionKey 清理）
- [√] 1.1 UV：将区间统计 unionKey 改为随机临时 key，并在 finally 中 delete + 设置短 TTL 兜底，files:
  - `analytics-service/src/main/java/com/nowcoder/community/analytics/repo/RedisAnalyticsRepository.java`
  - `analytics-service/src/test/java/com/nowcoder/community/analytics/repo/RedisAnalyticsRepositoryTest.java`
  verify: why.md#requirement-区间统计不产生永久-unionkey
- [√] 1.2 DAU：对 BITOP OR 的 unionKey 采用同样的临时 key + 清理策略（并发安全），files:
  - `analytics-service/src/main/java/com/nowcoder/community/analytics/repo/RedisAnalyticsRepository.java`
  - `analytics-service/src/test/java/com/nowcoder/community/analytics/repo/RedisAnalyticsRepositoryTest.java`
  verify: why.md#requirement-区间统计不产生永久-unionkey

## 2. gateway（采集链路去 subscribe）
- [√] 2.1 重构 `AnalyticsCollectGlobalFilter`：移除手动 `.subscribe()`，将 principal 解析与 trySubmitDau 纳入返回 Mono（采集失败不影响主链路），files:
  - `gateway/src/main/java/com/nowcoder/community/gateway/filter/AnalyticsCollectGlobalFilter.java`
  - `gateway/src/test/java/com/nowcoder/community/gateway/filter/AnalyticsCollectGlobalFilterTest.java`
  verify: why.md#requirement-网关采集不使用手动-subscribe

## 3. gateway（traceId 注入合并）
- [√] 3.1 统一 trace header 常量引用：将对 `TraceIdGlobalFilter.*` 的引用替换为 `TraceIdSupport.*`（逐步收敛），files:
  - `gateway/src/main/java/com/nowcoder/community/gateway/filter/AuditLogGlobalFilter.java`
  - `gateway/src/main/java/com/nowcoder/community/gateway/filter/OriginGuardGlobalFilter.java`
  verify: why.md#requirement-traceid-注入单一化且一致
- [√] 3.2 删除/禁用 `TraceIdGlobalFilter` 并更新对应测试用例（保留 `TraceIdWebFilter` 作为唯一注入点），files:
  - `gateway/src/main/java/com/nowcoder/community/gateway/filter/TraceIdGlobalFilter.java`
  - `gateway/src/test/java/com/nowcoder/community/gateway/filter/TraceIdWebFilterTest.java`
  verify: why.md#requirement-traceid-注入单一化且一致
- [-] 3.3 若存在其他引用点，继续替换并补齐测试覆盖（按实际搜索结果拆分执行，保持每步 ≤2 文件），files: 以实际改动为准
  > Note: 已全局搜索并完成替换/删除，未发现额外引用点，无需追加该任务。

## 4. gateway（OriginGuard 反代兼容）
- [√] 4.1 新增“有效请求 origin”解析工具：在可信代理 CIDR 命中时解析 Forwarded/X-Forwarded-*，否则忽略，files:
  - `gateway/src/main/java/com/nowcoder/community/gateway/filter/ForwardedOriginResolver.java`
  - `gateway/src/test/java/com/nowcoder/community/gateway/filter/ForwardedOriginResolverTest.java`
  verify: why.md#requirement-originguard-兼容反代https-offload
- [√] 4.2 改造 `OriginGuardGlobalFilter.isSameOrigin`：使用 ForwardedOriginResolver 计算 effective scheme/host/port，files:
  - `gateway/src/main/java/com/nowcoder/community/gateway/filter/OriginGuardGlobalFilter.java`
  - `gateway/src/test/java/com/nowcoder/community/gateway/filter/OriginGuardGlobalFilterTest.java`
  verify: why.md#requirement-originguard-兼容反代https-offload

## 5. internal/ops（配置契约与 runbook 收敛）
- [√] 5.1 更新 internal/ops runbook：补齐路径→header→配置 key 映射表、break-glass 开关与 403 排障 checklist，files:
  - `.helloagents/modules/runbooks/internal-ops.md`
  verify: why.md#requirement-internalops-配置契约可操作可排障
  > Note: 已补齐 `/api/ops/**` 与 `/internal/**` 映射、like-backfill 覆盖、internal-token/ops-token key 速查与 403 排障清单。
- [√] 5.2 更新 internal-token 轮转 runbook：补齐 segment 规则、users 特例与轮转步骤，files:
  - `.helloagents/modules/runbooks/internal-token-rotation.md`
  verify: why.md#requirement-internalops-配置契约可操作可排障
  > Note: 已补齐 users 高权限写入口（`users.ops.internal-token*`）特例，并补齐“gateway 作为 caller（注入 internal-token）”的轮转提示。
- [-] 5.3 （可选）增强 prod 启动期校验提示：与 trusted-proxy / OriginGuard / ops.guard 相关的关键配置缺失时给出更明确指引，files:
  - `common/src/main/java/com/nowcoder/community/common/startup/StartupValidation.java`
  - `common/src/test/java/com/nowcoder/community/common/startup/StartupValidationTest.java`
  verify: why.md#requirement-internalops-配置契约可操作可排障
  > Note: `StartupValidation` 已覆盖 trusted-proxy 与 ops-guard 的 prod fail-closed 校验；本次以 runbook 收敛为主，避免扩大代码改动面。

## 6. Security Check
- [√] 6.1 安全检查：forwarded 头信任边界、Origin allowlist、internal/ops token 爆炸半径与 fail-open/fail-closed 策略复核（无新增敏感信息落盘）。
  > Note: Forwarded/X-Forwarded-* 仅在 `gateway.trusted-proxy.enabled=true` 且 remoteAddr 命中 CIDR 时读取；非可信来源忽略转发头。未引入 token/PII 明文日志；analytics 临时 unionKey 查询后 delete 且 TTL 兜底，避免 Redis 膨胀。

## 7. Testing
- [√] 7.1 执行后端测试：`mvn test`（至少覆盖 gateway + analytics-service）；必要时补齐/调整 flaky 用例并复测。
  > Note: 已执行 `mvn -pl analytics-service test` 与 `mvn -pl gateway -am test` 通过；仓库缺少可用 `./mvnw` wrapper，因此使用系统 Maven。
