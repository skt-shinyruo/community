# 仅开发/演示（dev-only）

⚠️ 本文档包含“为了联调效率而存在”的便捷配置与演示账号信息：**仅适用于本地 dev / 演示环境**。  
生产环境（`SPRING_PROFILES_ACTIVE=prod`）必须遵循默认安全态（fail-closed），并通过配置中心/Secrets 注入真实密钥，**禁止**沿用本文的默认口令或固定验证码。

---

## 1) 默认演示账号（本地种子数据）

本地 compose（dev profile）会初始化种子数据，包含以下演示账号：
- 普通用户：`aaa/aaa`
- 管理员：`admin/aaa`

数据来源：
- `deploy/mysql-init/090_seed_identity.sql`

建议：
- 本地也尽量修改默认口令（至少不要在任何共享环境/公网环境复用）。

---

## 2) 验证码固定值（仅 dev）

为便于冒烟/联调，`dev` profile 下允许固定验证码：
- 配置：`backend/auth-service/src/main/resources/application-dev.yml`
- 固定值：`auth.captcha.fixed-code=0000`
- 冒烟脚本：`backend/scripts/smoke-i0-auth.sh`（`SMOKE_ONBOARDING=true` 时会使用固定验证码）

生产约束（SSOT）：
- prod profile 下 **禁止** `auth.captcha.fixed-code`，且 `backend/platform/common/StartupValidation` 会在启动期 fail-closed 阻断误配。

---

## 3) 敏感链接回传（仅 dev/演练）

本地 dev 默认可回传 onboarding 链接（便于无 SMTP 环境闭环）：
- `AUTH_EXPOSE_ACTIVATION_LINK=true`
- `AUTH_EXPOSE_RESET_LINK=true`

生产约束（SSOT）：
- prod profile 下禁止回传 activationLink/resetLink，并要求启用 SMTP（见 `docs/SECURITY.md` 与 `deploy/README.md`）。
