# Security Check 报告（生产级收尾）

Directory: `.helloagents/archive/2026-01/202601170935_legacy_cutover_prod_parity/`

> 目标：把“安全检查”固化为可执行清单与可追溯证据，避免仅靠人工经验。

---

## 1. 执行范围

覆盖以下维度：
- 输入校验与常见注入/XSS 风险
- 敏感信息与密钥管理
- 权限控制边界（网关 + 服务双层）
- 审计日志（可追踪到用户与 traceId）
- 限流风控（登录/敏感操作/管理操作）

---

## 2. 已落地的安全措施（代码/配置）

### 2.1 输入校验
- Controller 层普遍使用 `@Valid` + DTO 约束（`@NotBlank/@Email/@Min` 等）。
- 内容域对 `title/content` 做 `HtmlUtils.htmlEscape` + `SensitiveFilter`（降低 XSS 与敏感词风险）。

### 2.2 敏感信息与密钥
- 仓库提供 `scripts/secret-scan.sh` 并在 CI 中执行（best-effort）。
- JWT HMAC secret 统一通过环境变量/Nacos 注入，且校验长度（建议 >= 32 字节）。
- 内部接口（analytics/search）通过 `X-Internal-Token` 保护写入口。

### 2.3 权限控制（双层）
- gateway：对 `/api/**` 做 JWT 验签与角色矩阵（ADMIN/MODERATOR）。
- 服务侧：各服务 `SecurityConfig` 再次校验角色与内部接口 token（防止绕过网关直连）。

### 2.4 审计日志
- gateway：`AuditLogGlobalFilter` 记录非 GET 的操作（含 status/costMs/userId/traceId），并跳过 `/api/auth/login`。
- 服务侧：`common.web.AuditLogFilter` 记录 `/api/**` 与 `/internal/**` 的非 GET 操作（含 status/costMs/userId/traceId）。

### 2.5 限流风控
- auth-service：登录失败计数限流（Redis），按 IP + 用户双维度。
- gateway：Redis 限流（多规则），覆盖登录/注册/验证码/发帖/评论/点赞关注/私信/审核等，并输出 `X-RateLimit-*`。
- 可观测：Prometheus 指标 `gateway_rate_limit_total{rule, outcome}` + 告警规则（拦截激增、Redis 异常）。

---

## 3. 可执行检查入口

- 一键脚本：`scripts/security-check.sh`
  - secret scan
  - compose config 校验
  - 后端单测
  - 前端单测 + build（可用 `SKIP_FRONTEND=true` 跳过）

---

## 4. 未覆盖/后续增强（非阻塞项）

- 依赖漏洞扫描（SCA）：可按需引入 OWASP Dependency Check / Trivy 等作为 CI 可选门禁。
- WAF/更细粒度风控：按业务高风险点（注册/私信/发帖）加“验证码/滑块/设备指纹”等策略（超出当前仓库范围）。
