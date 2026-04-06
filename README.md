# community

本仓库采用 “frontend + backend” 的 monorepo 结构：

- `frontend/`：Vue3 SPA（Vite）
- `backend/`：后端工程（Java 17 + Spring Boot 3 + Maven 多模块）

## 快速开始（本地）

全栈容器一键启动（gateway-first，推荐）：

```bash
cp deploy/.env.example deploy/.env
make up
```

可选启动命令：
- `make up-obs`：叠加 Grafana / Loki / Prometheus / Alertmanager
- `make up-elastic`：叠加 Kibana / EDOT collector / fielded logs
- `make up-elastic-json`：在 Elastic 路径上再把 backend stdout 切到 JSON
- `make up-debug`：临时开放 localhost-only 直连排障端口

访问：
- 前端：`http://localhost:12881`
- 统一入口：`http://localhost:12880/api/...`
- 调试直连端口（需 `make up-debug`）：`12882 / 18081 / 18082`
- Kibana（需 `make up-elastic` 或 `make up-elastic-json`）：`http://localhost:12889`
- Elasticsearch localhost 入口（需 `make up-elastic` 或 `make up-elastic-json`）：`http://localhost:12888`

## 文档入口
- 后端工程：`backend/README.md`
- 部署与本地启动：`deploy/README.md`
- 架构/设计文档：`docs/README.md`
