# community

本仓库采用 “frontend + backend” 的 monorepo 结构：

- `frontend/`：Vue3 SPA（Vite）
- `backend/`：后端工程（Java 17 + Spring Boot 3 + Maven 多模块）

## 快速开始（本地）

后端 + 前端容器一键启动（推荐）：

```bash
cp deploy/.env.example deploy/.env
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env up -d --build
```

访问：
- 前端：`http://localhost:12881`
- 后端：`http://localhost:12882/api/...`

## 文档入口
- 后端工程：`backend/README.md`
- 部署与本地启动：`deploy/README.md`
- 架构/设计文档：`docs/README.md`
