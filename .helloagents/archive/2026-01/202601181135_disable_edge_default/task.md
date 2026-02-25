# 任务清单（Lightweight Iteration）

目标：在“前端直连 gateway（frontend:12881 / gateway:12882）”本地模式下，彻底移除 `community-edge`（Nginx）入口，统一以双端口直连运行。

## Tasks
- [√] 删除 edge 组件：移除 `deploy/docker-compose.yml` 中的 `edge` 服务，并删除 `deploy/Dockerfile.edge`、`deploy/edge/*`
- [√] 直连模式对外端口与激活链接默认值统一：frontend `12881`、gateway `12882`；`AUTH_ACTIVATION_BASE_URL` 默认指向 `http://localhost:12881`
- [√] 同步文档与知识库：README、infra/gateway/frontend 模块文档、runbook

## Notes
- 如需单端口入口（例如 80/443/TLS），建议在生产环境使用 Ingress/反代（Nginx/Traefik/Envoy）前置 gateway；本仓库本地 compose 不再内置 edge。
