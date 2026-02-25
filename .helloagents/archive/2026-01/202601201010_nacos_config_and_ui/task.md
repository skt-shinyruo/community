# Task List: Nacos 配置化（gateway allowlist）+ 暴露 Nacos UI 端口

Directory: `.helloagents/archive/2026-01/202601201010_nacos_config_and_ui/`

---

## 1. deploy（Nacos UI 端口暴露 + 示例配置）
- [√] 1.1 新增 compose overlay `deploy/docker-compose.nacos-ui.yml`：暴露 Nacos UI 端口 `8848:8848`，默认 compose 不变，verify why.md#requirement-能从宿主机访问-nacos-控制台-Scenario-通过-overlay-暴露-8848
- [√] 1.2 更新 `deploy/nacos-config/gateway.yaml`：补齐 gateway 的 CORS allowlist 与 OriginGuard allowlist 配置示例，verify why.md#requirement-通过-nacos-ui-可修改-gateway-allowlist-Scenario-在-nacos-ui-中编辑-gatewayyaml
- [√] 1.3 更新 `deploy/nacos-config/README.md`：说明需要 overlay 暴露 UI 端口，并给出创建/编辑 `gateway.yaml` 的步骤
- [√] 1.4 更新 `deploy/README.md`：补充“如何暴露 Nacos UI + 如何用 Nacos 管理配置”的入口说明

## 2. Knowledge Base Update
- [√] 2.1 更新 `.helloagents/modules/gateway.md`：说明 Nacos Config 的 Data ID、allowlist 配置入口与排查路径
- [√] 2.2 更新 `.helloagents/CHANGELOG.md`：记录新增的 Nacos UI overlay 与 Nacos 配置示例变更

## 3. Verification
- [√] 3.1 执行 `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.nacos-ui.yml config` 校验 YAML 合并（可选）
