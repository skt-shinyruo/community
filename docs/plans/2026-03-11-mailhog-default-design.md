# Default MailHog + SMTP in Base Compose (Design)

**Date:** 2026-03-11

## Context / Problem

当前本仓库的本地启动方式为：

- 基础 compose：`deploy/docker-compose.yml`
- 可选 profile：`observability`（观测/日志栈）

在 onboarding（注册激活/找回密码）场景中，基础 compose 默认是“无 SMTP”，通过回传 `activationLink/resetLink` 形成闭环。

这在联调上很方便，但也有两个问题：

1) 体验与生产不一致：生产应通过邮件完成激活/重置闭环；  
2) “敏感链接回传”更像 dev-only 的捷径，容易被误用在非纯本地环境。

因此希望将 MailHog 作为本地默认能力：一条命令启动后就具备 SMTP 收件箱，并默认关闭敏感链接回传。

## Goals

- `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build` 默认启动 MailHog
- 默认启用 SMTP 邮件发送（指向 MailHog）：
  - `AUTH_MAIL_ENABLED=true`
  - `SPRING_MAIL_HOST=mailhog`
  - `SPRING_MAIL_PORT=1025`
  - 关闭 `AUTH_EXPOSE_ACTIVATION_LINK/RESET_LINK`
- MailHog UI 仅绑定本机端口：`127.0.0.1:8025`
- 仍允许通过 `deploy/.env` 覆盖上述行为（例如禁用邮件、回传链接、或切换真实 SMTP）
- 更新文档，明确“默认邮件闭环”与“dev-only 回传链接”两种模式

## Non-goals

- 不引入真实 SMTP（如 SMTP relay / SES / SendGrid）或任何生产邮件投递配置
- 不把 SMTP 端口 `1025` 映射到宿主机（仅容器内网络通信即可）
- 不修改业务代码的注册/找回密码逻辑与邮件模板

## Proposed Changes

### 1) Base compose 内置 MailHog

在 `deploy/docker-compose.yml` 中新增 `mailhog` service：

- 镜像：`mailhog/mailhog:v1.0.1`
- `container_name: community-mailhog`
- 端口映射：`127.0.0.1:8025:8025`

并让 `community-app` 默认启用 SMTP 并指向 `mailhog`：

- `AUTH_MAIL_ENABLED` 默认值改为 `true`
- `AUTH_EXPOSE_ACTIVATION_LINK/RESET_LINK` 默认值改为 `false`
- 增加 Spring Mail 相关 env（host/port/auth/starttls）
- `community-app` 添加 `depends_on: [mailhog]`（避免应用启动时 SMTP hostname 不可解析）

### 2) `.env.example` 同步默认策略

在 `deploy/.env.example` 将 onboarding 默认策略从“回传链接（无 SMTP）”调整为“MailHog 收件箱（有 SMTP）”。

### 3) 文档更新（SSOT）

更新以下文档，使其与新的默认行为一致，并给出切换方式：

- `deploy/README.md`
- `docs/DEPLOYMENT.md`
- `docs/ARCHITECTURE.md`
- `docs/DEV_ONLY.md`

### 4) 历史 overlay 的处理

删除 `deploy/docker-compose.mailhog.yml`（不再需要通过额外 overlay 启用 MailHog）。

## Verification

- `docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config >/dev/null`
- 可选：`cp deploy/.env.example deploy/.env && docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`
- 浏览器访问 MailHog UI：`http://localhost:8025`
