# Technical Design: Nacos 配置化（gateway allowlist）+ 暴露 Nacos UI 端口

## Technical Solution

### Core Technologies
- Docker Compose overlays（按需暴露端口）
- Nacos Config（Data ID: `${spring.application.name}.yaml`）
- Spring Boot `spring.config.import: optional:nacos:...`（Nacos 覆盖本地默认配置）

### Implementation Key Points
1. **Nacos 配置示例补齐**
   - `deploy/nacos-config/gateway.yaml` 增加：
     - `spring.cloud.gateway.globalcors.corsConfigurations.[/**].allowedOrigins`（CORS allowlist）
     - `gateway.origin-guard.allowed-origins`（OriginGuard allowlist）
   - 通过 Nacos 控制台创建 `Data ID = gateway.yaml` 后，可直接在 UI 修改并保存。

2. **暴露 Nacos UI 端口（可选）**
   - 新增 `deploy/docker-compose.nacos-ui.yml` overlay：仅对 `nacos` service 增加端口映射 `8848:8848`
   - 用户需要显式追加 `-f deploy/docker-compose.nacos-ui.yml` 才会暴露端口（默认不暴露）

3. **文档与知识库同步**
   - 更新 `deploy/nacos-config/README.md` 与 `deploy/README.md`：说明如何启动并访问 Nacos UI，如何创建/维护 `gateway.yaml`
   - 更新知识库（`.helloagents/modules/gateway.md` / `.helloagents/CHANGELOG.md`）记录该能力

## Security and Performance
- **Security:**
  - 默认 compose 不暴露 Nacos 端口；需要时用 overlay 显式开启
  - Nacos UI 仅建议用于本地/测试环境
- **Performance:** 配置源变更对性能无显著影响

## Testing and Deployment
- **Testing:**
  - `docker compose config` 校验 YAML 合并结果（可选）
- **Deployment:**
  - 本地启动示例：
    - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml -f deploy/docker-compose.nacos-ui.yml --env-file deploy/.env up -d --build`
  - 访问 Nacos 控制台：`http://localhost:8848/nacos`

