# backend

本目录是后端工程根目录（Java 17 + Spring Boot 3 + Maven 多模块）。
仓库根目录同时包含前端工程：`../frontend/`。

> 约定：仓库级入口（部署/文档）从仓库根目录执行；后端构建/测试从 `backend/` 执行。

## 全栈启动（docker compose，推荐：gateway-first）

> 从仓库根目录执行（见 `../deploy/README.md` 获取可选能力：观测/日志 profile 等）：

1. 准备环境变量：`cp deploy/.env.example deploy/.env`
2. 启动全栈：`docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`
3. 访问：
   - 前端：`http://localhost:12881`
   - 统一入口：`http://localhost:12880/api/...`
   - 调试直连端口（需 `COMPOSE_PROFILES=debug`）：`12882 / 18081 / 18082`

## 本地开发 / 测试
- 单测：`mvn test`
- 打包后端单体：`mvn -q -DskipTests -pl :community-app -am package`

## 目录结构（后端）
- `community-app/`：主业务单体与 deployable
- `community-gateway/`：统一 HTTP / WS edge
- `community-im/`：IM 聚合模块（下含 `im-common`、`im-core`、`im-realtime`）

## 仓库级入口
- 部署与 compose：`../deploy/`
- 文档：`../docs/`

## 文档
- `../docs/DEPLOYMENT.md`
- `../deploy/README.md`
- `../docs/SECURITY.md`
- `../docs/DATA_MODEL.md`
- `../docs/OBSERVABILITY.md`
