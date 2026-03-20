# community

本仓库采用 “frontend + backend” 的 monorepo 结构：

- `frontend/`：Vue3 SPA（Vite）
- `backend/`：后端工程（Java 17 + Spring Boot 3 + Maven 多模块）

## 快速开始（本地）

全栈容器一键启动（gateway-first，推荐）：

```bash
cp deploy/.env.example deploy/.env
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

（可选）开启观测/日志（Grafana/Loki/Prometheus/Alertmanager）：

```bash
# 在 deploy/.env 中添加：COMPOSE_PROFILES=observability
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

访问：
- 前端：`http://localhost:12881`
- 统一入口：`http://localhost:12880/api/...`
- 调试直连端口（需 `COMPOSE_PROFILES=debug`）：`12882 / 18081 / 18082`

## 文档入口
- 后端工程：`backend/README.md`
- 部署与本地启动：`deploy/README.md`
- 架构/设计文档：`docs/README.md`
