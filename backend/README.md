# backend

本目录是后端工程根目录（Java 17 + Spring Boot 3 + Maven 多模块）。
仓库根目录同时包含前端工程：`../frontend/`。

> 约定：后端相关文档与脚本默认从 `backend/` 目录执行。

## 快速开始（推荐：前端直连后端单体）

1. 准备环境变量：
   - `cp deploy/.env.example deploy/.env`
2. 启动全栈（前端 `12881` / 后端 `12882`）：
   - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env up -d --build`
3. 访问：
   - 前端：`http://localhost:12881`
   - API：`http://localhost:12882/api/...`

## 本地开发 / 测试
- 单测：`./mvnw test`
- 打包后端单体：`./mvnw -q -DskipTests -pl :community-app -am package`

## 目录结构（后端）
- `app/community-app/`：单体后端入口（唯一 deployable）
- `platform/`：contracts/infra/common（跨模块稳定协议与横切能力）
- `content/`、`social/`、`user/`、`message/`、`search/`、`analytics/`、`ops-service/`：领域模块（作为库被 `community-app` 依赖）
- `gateway/`：legacy（历史网关/微服务形态，仅作为迁移窗口参考）
- `deploy/`：docker compose、Dockerfile、MySQL init、observability
- `scripts/`：冒烟/运维脚本
- `docs/`：后端文档

## 文档
- `docs/DEPLOYMENT.md`
- `deploy/README.md`
- `docs/SECURITY.md`
- `docs/DATA_MODEL.md`
- `docs/OBSERVABILITY.md`
