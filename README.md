# community

本仓库采用 monorepo 结构：

- `frontend/`：Vue3 SPA（Vite）
- `backend/`：Java 17 + Spring Boot 3 + Maven 多模块

## 快速开始

推荐先用单机开发拓扑：

```bash
cp deploy/.env.single.example deploy/.env.single
./deploy/deployment.sh up --topology single
```

常用变体：

- 单机基础设施：`./deploy/deployment.sh up --topology single --scope infra`
- 集群全栈：`./deploy/deployment.sh up --topology cluster`
- 追加观测层：`./deploy/deployment.sh up --topology single --observability`

默认访问地址：

- 前端：`http://localhost:12881`
- 统一入口：`http://localhost:12880`
- Kibana：`http://localhost:12889`（需 `--observability`）
- Elasticsearch：`http://localhost:12888`（需 `--observability`）

## 文档入口

- 文档手册：`docs/handbook/readme.md`
- 前端工程：`frontend/README.md`
- 后端工程：`backend/README.md`
- 本地部署：`deploy/README.md`
- 前端核心逻辑：`docs/handbook/frontend.md`
- 本地开发：`docs/handbook/local-development.md`
- 测试策略：`docs/handbook/testing.md`
- 运行排障：`docs/handbook/operations.md`
- 架构文档：`docs/handbook/architecture.md`
