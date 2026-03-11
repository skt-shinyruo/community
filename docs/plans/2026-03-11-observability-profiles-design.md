# Simplify `deploy/` with Observability Profiles (Design)

**Date:** 2026-03-11

## Context / Problem

当前本仓库使用多 compose 文件做“基础 + overlay”分层：

- `deploy/docker-compose.yml`：业务栈（frontend + community-app + IM + MySQL/Redis/Kafka/ES + MailHog）
- `deploy/docker-compose.observability.yml`：可选观测/日志栈（Prometheus/Grafana/Loki/Promtail/Alertmanager）

这会带来两个摩擦点：

1) `deploy/` 文件数量多、文档需要解释 overlay；  
2) 启用观测需要额外记住 `-f deploy/docker-compose.observability.yml`，命令更长。

目标是在**不改变默认命令形态**（仍使用 `-f` 与 `--env-file`）的前提下，减少文件数量并简化开启观测的方式。

## Goals

- 将 `deploy/docker-compose.observability.yml` 合并回 `deploy/docker-compose.yml`
- 观测栈默认不启动，通过 Compose `profiles` 控制（profile 名：`observability`）
- 允许通过 `deploy/.env` 设置 `COMPOSE_PROFILES=observability` 一键开启观测（无需额外 `-f`）
- 保持观测端口继续只绑定到 `127.0.0.1`（安全默认）
- 更新文档与示例命令，移除 overlay 文件依赖

## Non-goals

- 不调整观测组件版本、配置内容与端口号（仍使用 `deploy/observability/*` 的现有配置）
- 不引入新的脚本/Makefile 作为强制入口（可以保留显式 compose 命令）
- 不改变业务栈对外端口与依赖暴露策略（fail-closed）

## Proposed Changes

### 1) Base compose 内置可选观测栈

将以下 services 从 overlay 合并到 `deploy/docker-compose.yml`，并为每个 service 加上：

- `profiles: [observability]`

涉及服务：
- `prometheus`
- `grafana`
- `loki`
- `promtail`
- `alertmanager`

相关数据卷定义（`prometheus_data/grafana_data/loki_data`）也在 `deploy/docker-compose.yml` 的 `volumes:` 中声明。

### 2) 启动方式（仍保留 `-f` 与 `--env-file`）

默认启动（无观测）：

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

开启观测（推荐：通过 `deploy/.env`）：

- 在 `deploy/.env` 增加：`COMPOSE_PROFILES=observability`
- 然后执行同一条启动命令即可

### 3) 删除 overlay 文件

删除 `deploy/docker-compose.observability.yml`，避免双入口与文档漂移。

## Verification

- `docker compose -f deploy/docker-compose.yml --env-file deploy/.env.example config >/dev/null`
- 可选：设置 `COMPOSE_PROFILES=observability` 后 `docker compose ... config` 仍可解析

