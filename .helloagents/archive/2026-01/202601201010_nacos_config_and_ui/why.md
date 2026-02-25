# Change Proposal: Nacos 配置化（gateway allowlist）+ 暴露 Nacos UI 端口

## Requirement Background
当前项目已接入 Nacos Discovery/Config，并在各服务 `application.yml` 中通过 `spring.config.import: optional:nacos:${spring.application.name}.yaml` 支持从 Nacos 覆盖配置。

为了便于在本地/测试环境快速调整配置（例如 gateway 的 CORS allowlist、OriginGuard allowlist），希望这些配置可以直接在 Nacos 控制台修改，而无需频繁改动仓库内的配置文件。

同时，当前默认 docker compose 为安全起见不暴露 Nacos 等内部依赖端口到宿主机，导致无法直接打开 Nacos 控制台进行配置维护，需要提供一个可选的端口暴露方案。

## Change Content
1. 补齐 `deploy/nacos-config/gateway.yaml` 示例：把 gateway 的 CORS allowlist 与 OriginGuard allowlist 写入 Nacos 配置示例（SSOT 在 Nacos）。
2. 新增可选 compose overlay：暴露 Nacos UI 端口（`8848`）到宿主机，便于在浏览器直接编辑配置。
3. 更新部署/配置说明文档：明确如何启动带 UI 端口暴露的 compose，以及如何在 Nacos 中创建 `Data ID = gateway.yaml` 的配置。

## Impact Scope
- **Modules:** `deploy` / `gateway`（配置来源）/ docs / knowledge base
- **Files:** compose overlays、`deploy/nacos-config/*`、相关文档与知识库条目

## Core Scenarios

### Requirement: 通过 Nacos UI 可修改 gateway allowlist
**Module:** deploy / gateway

#### Scenario: 在 Nacos UI 中编辑 gateway.yaml
在 Nacos 控制台创建/修改 `gateway.yaml`（YAML）后：
- gateway 能从 Nacos 加载并生效（至少重启后生效）
- allowlist 变更不需要改仓库内 `application.yml`

### Requirement: 能从宿主机访问 Nacos 控制台
**Module:** deploy

#### Scenario: 通过 overlay 暴露 8848
以 compose overlay 启动后：
- `http://localhost:8848/nacos` 可访问
- 仍保持默认 compose 不主动暴露内部依赖端口（安全默认不变）

## Risk Assessment
- **Risk:** 暴露 Nacos 端口到宿主机可能增加本地环境误操作与安全暴露面。
- **Mitigation:** 采用 overlay 文件显式开启（默认不暴露），并在文档中强调仅用于本地/测试环境。

