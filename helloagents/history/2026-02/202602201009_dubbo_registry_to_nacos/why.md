# Change Proposal: Dubbo 注册中心收敛到 Nacos

## Requirement Background
当前系统同时存在两套“注册中心/治理链路”：

1. **Spring Cloud / Nacos**：用于配置中心（`spring.config.import`）与服务发现（`spring.cloud.nacos.discovery`）。
2. **Dubbo / Zookeeper**：用于 RPC 的服务注册与发现（`dubbo.registry.address=zookeeper://...`）。

这会导致同一套服务需要面对两套注册/健康/配置/排障路径，长期会显著增加运维与开发心智负担，并提高环境一致性与故障定位成本。

本变更的目标是：**将 Dubbo 的注册中心从 Zookeeper 迁移到 Nacos**，使系统的“注册中心”收敛为 Nacos（Zookeeper 仅保留给 Kafka 等既有依赖，后续可单独治理）。

## Success Criteria
以下条件满足时视为本治理项完成（可验收、可回滚）：

1. 默认情况下（仅设置 `NACOS_SERVER_ADDR`），所有服务 Dubbo 调用链可用，且不再依赖 `zookeeper://...` registry。
2. `deploy/docker-compose.yml` 启动全依赖后，业务服务不再注入 `DUBBO_REGISTRY_ADDR`，仍可正常启动与互相调用。
3. `mvn test` 通过（最少保证：不引入编译/依赖冲突，基础用例可运行）。
4. Nacos UI 可观测到相关服务的注册信息（用于排障与对照）；并能区分 Spring Cloud 服务实例与 Dubbo provider/consumer 的注册信息（通过 group/namespace 规划或命名约定）。
5. 文档同步：`README.md`、`helloagents/wiki/arch.md`、`helloagents/CHANGELOG.md` 与代码现状一致。

## Non-Goals
本变更明确不做以下事项（避免把“收敛 registry”扩成大迁移）：

- 不移除 Dubbo（RPC 形态保持不变）。
- 不将服务间同步调用迁移为 HTTP/Feign（不改调用链语义）。
- 不移除 Zookeeper（Kafka 仍可能依赖；Kafka 去 ZK/KRaft 作为后续独立治理项）。
- 不做 Nacos 集群化/容量调优的深度改造（仅在文档中提示容量与隔离注意事项）。

## Change Content
1. **配置语义收敛（默认 Nacos，保留应急覆盖）：**
   - Dubbo 默认 registry：`nacos://${NACOS_SERVER_ADDR:127.0.0.1:8848}`
   - 保留应急覆盖：`DUBBO_REGISTRY_ADDR`（显式设置时覆盖默认值；默认不在 compose 注入，避免双栈常态化）。
2. **隔离策略对齐：** Dubbo registry 对齐 `NACOS_GROUP`（以及可选的 `NACOS_NAMESPACE`），避免 Spring Cloud 与 Dubbo 使用不同隔离维度导致串扰。
3. **依赖收敛：** Maven 依赖层面将 `dubbo-registry-zookeeper` 替换为 `dubbo-registry-nacos`，避免同时携带两套 registry 客户端与传递依赖。
4. **部署编排收敛：**
   - `deploy/docker-compose.yml` 仍启动 Nacos 与 Zookeeper（ZK 给 Kafka 用），但业务服务默认不再注入 `DUBBO_REGISTRY_ADDR`、也不再依赖 ZK 启动顺序。
   - （可选）生产灰度阶段可暂时保留 `DUBBO_REGISTRY_ADDR=zookeeper://...` 以实现“先发版不换 registry”，待验证后再切换到 `nacos://...`。
5. **文档同步：** 更新知识库与项目文档，使“当前架构”与“目标架构”一致（Dubbo registry 收敛到 Nacos），并明确 Zookeeper 仅承担 Kafka 依赖。

## Impact Scope
- **Modules:**
  - gateway
  - auth-service
  - user-service
  - content-service
  - social-service
  - message-service
  - search-service
  - analytics-service
- **Files (expected):**
  - `deploy/docker-compose.yml`
  - `*/src/main/resources/application.yml`（Dubbo registry 配置）
  - `*/pom.xml`（Dubbo registry 依赖）
  - `README.md`
  - `helloagents/wiki/arch.md`
  - `helloagents/CHANGELOG.md`
- **APIs:** 无（不改变对外 API 形态）
- **Data:** 无（不涉及数据模型迁移）

## Core Scenarios

### Requirement: Dubbo Registry Convergence
**Module:** Infra / Governance
将 Dubbo 的服务注册与发现统一到 Nacos，消除 Dubbo→Zookeeper 的 registry 依赖与注入变量。

#### Scenario: Default registry uses Nacos
环境仅提供 `NACOS_SERVER_ADDR`（以及可选的 `NACOS_GROUP/NACOS_NAMESPACE`）。
- 服务启动后能正常完成 Dubbo provider/consumer 的注册与发现。
- Nacos UI 中可观测到对应服务实例（用于排障与验证）。
 - `deploy/docker-compose.yml` 不再注入 `DUBBO_REGISTRY_ADDR` 时，仍能运行全链路。

#### Scenario: Emergency rollback (optional override)
在不修改镜像的前提下，允许通过显式设置 `DUBBO_REGISTRY_ADDR` 进行紧急回滚。
- 默认不在 docker-compose 注入该变量（避免双栈常态化）。
- 仅作为应急手段，后续可在稳定后移除该“后门”配置。

## Risk Assessment
- **Risk:** Dubbo registry 迁移期间出现“找不到服务/调用失败/启动卡死”等问题。
  - **Mitigation:** 提供应急回滚开关（`DUBBO_REGISTRY_ADDR` 覆盖）；本地 compose 与 CI 先行验证；上线采用灰度或分批发布。
- **Risk:** Nacos 负载上升或隔离策略不一致（group/namespace 混用）导致跨环境串扰。
  - **Mitigation:** Dubbo registry 显式对齐 `NACOS_GROUP/NACOS_NAMESPACE` 约定；生产环境建议启用 namespace 隔离与权限控制。
- **Risk:** Zookeeper 仍因 Kafka 保留，容易产生“已收敛但依赖仍在”的误解。
  - **Mitigation:** 文档明确 Zookeeper 仅为 Kafka（后续可单独推进 Kafka KRaft 去 ZK）。
