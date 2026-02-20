# Task List: Dubbo 注册中心收敛到 Nacos

Directory: `helloagents/plan/202602201009_dubbo_registry_to_nacos/`

---

## 0. 迁移策略确认（避免“写完就替换”的高风险操作）
- [√] 0.1 确认采用迁移路径：
  - 路径 A（推荐）：发布窗口内一次性切换所有服务到 Nacos registry（最简单）
  - 路径 B（可选）：双 registry 并行灰度（更稳但更复杂，需要额外配置与验证）
- [√] 0.2 固化应急回滚动作：明确当 Nacos registry 异常时，通过显式设置 `DUBBO_REGISTRY_ADDR=zookeeper://...` 回切，并记录验证点（Nacos/ZK 可观测、调用链恢复）
- [√] 0.3 明确 Nacos 隔离策略：
  - `NACOS_NAMESPACE` 是否启用（推荐生产启用）
  - `NACOS_GROUP` 的命名规范（例如按环境/业务域）
  - 目标：Spring Cloud 与 Dubbo 使用同一套 namespace/group，避免跨环境串扰
- [√] 0.4 明确 Dubbo 注册信息“可观测校验口径”：
  - 期望在 Nacos UI 看到哪些注册条目（服务名/实例/元数据）
  - 迁移后排障入口统一（禁止再以“Dubbo 去 ZK 查”为默认路径）

## 1. Maven 依赖收敛（Dubbo registry）
- [√] 1.1 将 `gateway/pom.xml` 的 `dubbo-registry-zookeeper` 替换为 `dubbo-registry-nacos`，verify why.md#requirement-dubbo-registry-convergence
- [√] 1.2 将 `auth-service/pom.xml` 的 `dubbo-registry-zookeeper` 替换为 `dubbo-registry-nacos`，verify why.md#requirement-dubbo-registry-convergence
- [√] 1.3 将 `user-service/pom.xml` 的 `dubbo-registry-zookeeper` 替换为 `dubbo-registry-nacos`，verify why.md#requirement-dubbo-registry-convergence
- [√] 1.4 将 `content-service/pom.xml` 的 `dubbo-registry-zookeeper` 替换为 `dubbo-registry-nacos`，verify why.md#requirement-dubbo-registry-convergence
- [√] 1.5 将 `social-service/pom.xml` 的 `dubbo-registry-zookeeper` 替换为 `dubbo-registry-nacos`，verify why.md#requirement-dubbo-registry-convergence
- [√] 1.6 将 `message-service/pom.xml` 的 `dubbo-registry-zookeeper` 替换为 `dubbo-registry-nacos`，verify why.md#requirement-dubbo-registry-convergence
- [√] 1.7 将 `search-service/pom.xml` 的 `dubbo-registry-zookeeper` 替换为 `dubbo-registry-nacos`，verify why.md#requirement-dubbo-registry-convergence
- [√] 1.8 将 `analytics-service/pom.xml` 的 `dubbo-registry-zookeeper` 替换为 `dubbo-registry-nacos`，verify why.md#requirement-dubbo-registry-convergence
- [√] 1.9 全局校验：确保代码侧不再显式依赖 `dubbo-registry-zookeeper`（避免传递依赖/遗漏模块），并确保构建可通过
- [√] 1.10 依赖兼容性校验：确认引入 `dubbo-registry-nacos` 后不会与 Spring Cloud Alibaba Nacos 的依赖版本产生冲突（必要时通过 dependencyManagement 固定关键依赖版本）

## 2. 应用配置收敛（Dubbo registry address）
- [√] 2.1 统一 Dubbo registry 配置模式：采用 `${DUBBO_REGISTRY_ADDR:nacos://${NACOS_SERVER_ADDR:...}}` 的嵌套占位符表达默认值与覆盖，避免在 compose 中长期注入 `DUBBO_REGISTRY_ADDR`，verify why.md#scenario-default-registry-uses-nacos
- [√] 2.2 统一隔离与鉴权参数：为 Dubbo registry 增加 `namespace/group/username/password` 参数映射（对齐 `NACOS_NAMESPACE/NACOS_GROUP/NACOS_USERNAME/NACOS_PASSWORD`），verify why.md#scenario-default-registry-uses-nacos
- [√] 2.3 明确 Dubbo registry 参数的最终写法（以运行验证为准）：
  - 优先：`dubbo.registry.parameters.namespace/group/...`
  - 若参数名不生效：调整为 Dubbo/Nacos registry 实际支持的字段（仍保持环境变量不变）
  - 输出：形成“目标配置片段”并在所有服务中统一落地
- [√] 2.4 调整 `gateway/src/main/resources/application.yml`：按 2.1/2.2/2.3 落地 Dubbo registry 配置，verify why.md#scenario-default-registry-uses-nacos
- [√] 2.5 调整 `auth-service/src/main/resources/application.yml`：按 2.1/2.2/2.3 落地 Dubbo registry 配置，verify why.md#scenario-default-registry-uses-nacos
- [√] 2.6 调整 `user-service/src/main/resources/application.yml`：按 2.1/2.2/2.3 落地 Dubbo registry 配置，verify why.md#scenario-default-registry-uses-nacos
- [√] 2.7 调整 `content-service/src/main/resources/application.yml`：按 2.1/2.2/2.3 落地 Dubbo registry 配置，verify why.md#scenario-default-registry-uses-nacos
- [√] 2.8 调整 `social-service/src/main/resources/application.yml`：按 2.1/2.2/2.3 落地 Dubbo registry 配置，verify why.md#scenario-default-registry-uses-nacos
- [√] 2.9 调整 `message-service/src/main/resources/application.yml`：按 2.1/2.2/2.3 落地 Dubbo registry 配置，verify why.md#scenario-default-registry-uses-nacos
- [√] 2.10 调整 `search-service/src/main/resources/application.yml`：按 2.1/2.2/2.3 落地 Dubbo registry 配置，verify why.md#scenario-default-registry-uses-nacos
- [√] 2.11 调整 `analytics-service/src/main/resources/application.yml`：按 2.1/2.2/2.3 落地 Dubbo registry 配置，verify why.md#scenario-default-registry-uses-nacos
- [√] 2.12 生产 profile 校验：检查 `*/src/main/resources/application-prod.yml` 是否需要补充/限制 registry 相关配置（例如 prod 必须 fail-closed，不允许隐式 fallback），并确保与 `spring.config.import` 逻辑一致
- [-] 2.13 回滚覆盖验证：通过显式设置 `DUBBO_REGISTRY_ADDR=zookeeper://...` 启动任一服务，确认覆盖生效（但默认 compose 不注入），verify why.md#scenario-emergency-rollback-optional-override
  > Note: 已静态确认支持 `DUBBO_REGISTRY_ADDR` 覆盖，但未完成实际启动验证（需 Nacos/ZK 环境 + 全链路冒烟后再演练）。

## 3. 部署编排收敛（docker compose）
- [√] 3.1 更新 `deploy/docker-compose.yml`：移除业务服务 `DUBBO_REGISTRY_ADDR` 注入（默认不注入），verify why.md#scenario-default-registry-uses-nacos
- [√] 3.2 更新 `deploy/docker-compose.yml`：保留统一注入 `NACOS_SERVER_ADDR`（必要时同步注入 `NACOS_GROUP/NACOS_NAMESPACE/NACOS_USERNAME/NACOS_PASSWORD`），并补充注释说明变量语义与默认值
- [√] 3.3 更新 `deploy/docker-compose.yml`：逐个服务检查 `depends_on: zookeeper` 是否仍必要；在 Dubbo registry 不再依赖 ZK 的前提下，移除不必要的启动顺序依赖（ZK 仍可保留给 Kafka）
- [√] 3.4 全局检索：清理仓库中对 `DUBBO_REGISTRY_ADDR` 的默认注入/引用（保留 2.13 的应急覆盖约定），确保双栈不常态化
- [-] 3.5 （可选）为生产/灰度准备：补充“先发版不切换 registry”的操作说明（显式设置 `DUBBO_REGISTRY_ADDR=zookeeper://...`），待验证后再移除该注入
  > Note: 本次仅完成默认收敛与文档提示；未单独补齐生产灰度发布说明。

## 4. Documentation Update（知识库同步）
- [√] 4.1 更新 `README.md`：统一列出 Nacos 相关环境变量（`NACOS_SERVER_ADDR/GROUP/NAMESPACE/USERNAME/PASSWORD`）与 Dubbo registry 覆盖变量（`DUBBO_REGISTRY_ADDR` 仅应急），verify why.md#change-content
- [√] 4.2 更新 `README.md`：补充“迁移验证 checklist”（compose 启动、Nacos UI 可见、关键 Dubbo 调用链、回滚演练）
- [√] 4.3 更新 `helloagents/wiki/arch.md`：更新“当前总体架构（微服务全链路）”图，将 Dubbo registry 从 ZK 指向 Nacos；并在技术栈中明确 Zookeeper 仅用于 Kafka（如仍保留），verify why.md#requirement-dubbo-registry-convergence
- [√] 4.4 更新 `helloagents/CHANGELOG.md`：记录 registry 收敛的变更点（Changed/Removed）
- [√] 4.5 补充最小 runbook（README 或 wiki）：包含默认配置、常见故障定位路径（从 Nacos 入手）、回滚动作与验证点

## 5. Security Check
- [√] 5.1 执行安全检查（G9）：配置泄露、敏感信息打印、权限边界、回滚开关误用风险、生产环境隔离策略
  > Note: 本次为“变更审阅级”检查（未运行额外脚本），重点确认未引入明文凭证与默认注入双栈变量。
- [√] 5.2 生产安全护栏建议落地到文档：namespace 隔离、账号权限、避免把 Nacos 凭证写进镜像/日志

## 6. Testing
- [√] 6.1 运行后端测试：`mvn test`
- [X] 6.2 运行 compose 冒烟：`docker compose -f deploy/docker-compose.yml up -d --build`（本地），并检查服务健康状态（Nacos/网关/各服务）
  > Note: 已修复 compose 文件语法并通过 `docker compose ... config` 校验；但在拉取镜像阶段遇到网络问题（TLS handshake timeout）导致冒烟未完成。
- [-] 6.3 验证 Dubbo 关键调用链：至少验证 1 条依赖 Dubbo 的跨服务同步链路（例如 search reindex 拉取或任意跨服务只读 RPC）
  > Note: 依赖 6.2 启动全链路后验证。
- [-] 6.4 验证 Nacos 可视化与隔离：确认 Spring Cloud 服务与 Dubbo 注册信息位于预期 namespace/group 下，排障入口统一
  > Note: 依赖 6.2 启动全链路后在 Nacos UI 对照验证。
- [-] 6.5 回滚演练（本地即可）：显式设置 `DUBBO_REGISTRY_ADDR=zookeeper://...` 启动一组服务，确认覆盖生效且能恢复调用（用于验证应急手段可用）
  > Note: 依赖 6.2 启动成功后再进行回滚演练。
