# 仅开发/演示（dev-only）

⚠️ 本文档包含“为了联调效率而存在”的便捷配置与演示账号信息：**仅适用于本地 dev / 演示环境**。  
生产环境（`SPRING_PROFILES_ACTIVE=prod`）必须遵循默认安全态（fail-closed），并通过配置中心/Secrets 注入真实密钥，**禁止**沿用本文的默认口令或固定验证码。

---

## 1) 默认演示账号（本地种子数据）

本地默认 compose 分层会初始化种子数据，包含以下演示账号：
- 普通用户：`aaa/aaa`
- 管理员：`admin/aaa`

数据来源：
- `deploy/mysql-init/010_schema.sql`（包含本地 dev/demo 种子用户插入）

建议：
- 本地也尽量修改默认口令（至少不要在任何共享环境/公网环境复用）。

---

## 2) 验证码固定值（仅 dev）

为便于冒烟/联调，开发环境允许固定验证码：
- 配置：`backend/community-app/src/main/resources/application.yml` 或测试配置 `backend/community-app/src/test/resources/application.yml`
- 固定值：`auth.captcha.fixed-code=0000`
- 冒烟脚本：暂无（如需可在后续补充）

生产约束（SSOT）：
- 生产环境（`SPRING_PROFILES_ACTIVE=prod`）下 **禁止** `auth.captcha.fixed-code`，且启动期校验会 fail-closed 阻断误配（见 `backend/community-app/src/main/java/com/nowcoder/community/auth/config/AuthStartupValidator.java`）。

当前仓库说明：
- 验证码固定值配置键为 `auth.captcha.fixed-code`（见 `backend/community-app/src/main/java/com/nowcoder/community/auth/config/CaptchaProperties.java`）。
- prod 下的 fail-closed 校验实现位于 `backend/community-app/src/main/java/com/nowcoder/community/auth/config/AuthStartupValidator.java`。

---

## 3) 调试验证码/重置链接回传（仅 dev/演练）

本地 compose 默认不回传注册验证码/`resetLink`（更贴近生产安全态），而是通过 MailHog 接收邮件完成闭环：
- MailHog UI：`http://localhost:8025`（仅本机）

如需 dev-only 快捷模式（无 SMTP 也能跑通闭环），可显式开启回传：
- `AUTH_MAIL_ENABLED=false`
- `AUTH_REGISTRATION_EXPOSE_CODE=true`
- `AUTH_EXPOSE_RESET_LINK=true`

生产约束（SSOT）：
- 生产环境（`SPRING_PROFILES_ACTIVE=prod`）下禁止回传注册验证码/`resetLink`，并要求启用 SMTP（见 `docs/SECURITY.md` 与 `deploy/README.md`）。

---

## 4) Mock Data Studio（仅 dev / 演示）

本地 compose 现在包含 `mock-data-studio` dev-only 控制面，用于生成可删除的演示数据；当前阶段暴露：
- `GET /`
- `GET /health`
- `GET /api/runtime-status`
- `POST /api/jobs`
- `GET /api/jobs/:jobId`
- `GET /api/batches`
- `GET /api/batches/:batchId`
- `DELETE /api/batches/:batchId`
- 环境变量解析与启动日志

访问方式：
- `http://localhost:${MOCK_DATA_STUDIO_HOST_PORT:-12890}/`
- `http://localhost:${MOCK_DATA_STUDIO_HOST_PORT:-12890}/health`（仅绑定到宿主机 `127.0.0.1`）
- `http://localhost:${MOCK_DATA_STUDIO_HOST_PORT:-12890}/api/runtime-status`
- `http://localhost:${MOCK_DATA_STUDIO_HOST_PORT:-12890}/api/jobs`

当前默认开关：
- `MOCK_DATA_STUDIO_ENABLED=true`
- `MOCK_DATA_STUDIO_HOST_PORT=12890`
- `MOCK_DATA_STUDIO_PORT=12888`
- `MOCK_DATA_AUTO_FILL_ENABLED=false`
- `MOCK_DATA_AUTO_FILL_SCENE=tech-community-hot-start`
- `MOCK_DATA_DEFAULT_USERS=100`
- `MOCK_DATA_DEFAULT_POSTS=800`
- `MOCK_DATA_DEFAULT_COMMENTS=2500`

说明：
- `MOCK_DATA_STUDIO_PORT` 表示 studio 进程监听端口；`MOCK_DATA_STUDIO_HOST_PORT` 表示 compose 暴露到宿主机的 localhost-only 端口。当前 operator path 默认是宿主机 `12890 ->` 进程 `12888`。
- `MOCK_DATA_AUTO_FILL_ENABLED=true` 会在服务启动后自动提交一次缺口填充 job；若 job 失败，会通过现有 batch/job 元数据记录为 failed，而不是只写启动日志。
- auto-fill scene 目前支持 `tech-community-hot-start`、`moderation-pressure`、`im-busy`、`reward-ops-busy`。
- `tech-community-hot-start` 当前会同时补充：
  - 社区 Phase 1：`user` / `discuss_post` / `comment` / `social_follow` / `social_like`
  - 社区 Phase 2：`message`（私信 + notices）、`report`、`moderation_action`
  - growth / reward：`growth_check_in`、`user_task_progress`、`reward_account`、`reward_ledger`、`reward_grant_record`、`reward_item`、`reward_order`
  - IM：`im_core.im_room` / `im_room_member` / `im_room_message` / `im_conversation` / `im_private_message`
- 所有新增行都会记录到 `demo_entity_ref`，因此手动批次支持依赖顺序删除；批次详情页会按 target / actual / failure summary 展示这些 Phase 2 实体。
- 这些开关只代表 studio 侧本地控制面行为，不会改变 prod 的 fail-closed 安全约束。
