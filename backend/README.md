# backend

本目录是后端工程根目录（Java 17 + Spring Boot 3 + Maven 单后端模块）。
仓库根目录同时包含前端工程：`../frontend/`。

> 约定：仓库级入口（部署/文档）从仓库根目录执行；后端构建/测试从 `backend/` 执行。

## 全栈启动（docker compose，推荐：前端直连后端单体）

> 从仓库根目录执行（见 `../deploy/README.md` 获取更多 overlay 示例）：

1. 准备环境变量：`cp deploy/.env.example deploy/.env`
2. 启动全栈：`docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env up -d --build`
3. 访问：
   - 前端：`http://localhost:12881`
   - API：`http://localhost:12882/api/...`

## 本地开发 / 测试
- 单测：`mvn test`
- 打包后端单体：`mvn -q -DskipTests -pl :community-bootstrap -am package`

## 目录结构（后端）
- `community-bootstrap/`：唯一后端模块与 deployable
- `community-bootstrap/src/main/java/com/nowcoder/community/*`：按领域与基础设施包组织代码
- `community-bootstrap/src/main/resources/`：统一资源、mapper、自动配置清单

## 仓库级入口
- 部署与 compose：`../deploy/`
- 文档：`../docs/`

## 文档
- `../docs/DEPLOYMENT.md`
- `../deploy/README.md`
- `../docs/SECURITY.md`
- `../docs/DATA_MODEL.md`
- `../docs/OBSERVABILITY.md`
