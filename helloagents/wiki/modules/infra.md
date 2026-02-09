# infra（跨服务基础设施与交付）

## Purpose
提供“生产级可交付”的跨服务基础设施与通用能力：全依赖一键拉起、可观测性、备份/恢复、切换/回滚、CI 门禁、压测脚本等。

## Module Overview
- **Responsibility：**
  - 入口说明：`deploy/README.md`（deploy 目录结构说明 + 本地启动教程）
  - 部署：`deploy/docker-compose.yml`（Nacos/MySQL/Redis/Kafka/Elasticsearch/Zookeeper + 全服务 + 观测栈；基础 compose 默认不提供“前端统一入口”容器）
  - 前端直连（推荐）：`deploy/docker-compose.frontend-direct.yml`（对外暴露前端 `12881` + gateway `12882`，浏览器访问前端、前端跨端口直连 gateway，不依赖 Nginx）
  - 观测端口映射（可选）：`deploy/docker-compose.ports.yml`（仅暴露 Grafana/Loki/Prometheus/Alertmanager，端口 `12883+`）
  - 容器化：`deploy/Dockerfile.spring-service`（统一构建/健康检查）
  - MySQL 初始化：`deploy/mysql-init/`（同实例多 schema + 最小权限账号；支持清卷重建）
  - 备份/恢复：`scripts/mysql-backup.sh`、`scripts/mysql-restore.sh`、`scripts/redis-backup.sh`、`scripts/es-reset-index.sh`、`scripts/kafka-reset-topics.sh`
  - Kafka 运维：`scripts/kafka-replay-dlq.sh`（DLQ 回放，含白名单/限量/限速/默认 dry-run）
  - 安全门禁：`scripts/secret-scan.sh` + `scripts/security-check.sh`
    - 说明：`secret-scan` 仅扫描 git tracked 文件，避免本地未跟踪的 `deploy/.env`（gitignored）阻断安全检查；如误将 `deploy/.env` 加入版本控制会直接失败。
  - 质量门禁：CI（backend-test/frontend-lint-build）+ 冒烟脚本（可选：`scripts/smoke-i0-auth.sh`）
  - 压测：`loadtest/`（k6 脚本与容量阈值/记录模板）
- **Status：** ✅Stable
- **Last Updated：** 2026-01-20

## Specifications（交付门禁）

### Requirement: 全依赖可用
- MySQL/Redis/Kafka/Elasticsearch/Nacos 均启用且服务健康（`/actuator/health`）。
- CI 侧以单元测试/构建为基础门禁；全链路集成验证建议在本地按需执行（docker compose + curl）。
- Zookeeper 状态持久化（`zookeeper_data/zookeeper_log`），避免 Kafka 因 clusterId 不一致而启动失败；同时 Zookeeper 也作为 Dubbo registry（建议使用 chroot `/dubbo`）。
- Kafka 增加 compose healthcheck + `restart: on-failure`，并将依赖 Kafka 的服务统一为等待 `service_healthy`，避免首次启动时 Kafka 因 ZK 会话未过期窗口偶发退出导致级联失败。

### Requirement: 构建可重复、可恢复
- `deploy/Dockerfile.spring-service` 使用 BuildKit cache 复用 Maven 依赖（`/root/.m2/repository`）以加速构建。
- 若因网络波动导致 cache 中出现“损坏/空 jar”（典型报错：`ZipException` / `zip file is empty`），Dockerfile 会在首次构建失败时清空 cache 并自动重试一次，避免 `docker compose up -d --build` 进入“必然失败”状态。

### Requirement: 可观测与告警
- Metrics：各服务暴露 `/actuator/prometheus`，Prometheus 可抓取。
- Logs：Loki/Promtail 聚合日志；审计日志包含 traceId/userId 便于检索。
- Trace：兼容 `traceparent` + `X-Trace-Id`，可跨网关/服务串联。

## API Interfaces
- 管理接口：`/data/**`、`/actuator/**`（管理员权限）

## Dependencies
- 全模块共用（Redis/Kafka/Quartz/Security/Actuator）

## Change History
- 2026-01-17：容器构建增加 Maven cache 失败自愈（清理 `/root/.m2/repository` 并重试一次）。
- 2026-01-17：Zookeeper 数据持久化，避免 `InconsistentClusterIdException`（Kafka clusterId 不一致）。
- 2026-01-18：修复 `kafka-init` 脚本传参方式，确保 topics 初始化能被 `bash -lc` 正确执行。
- 2026-01-18：新增“前端直连 gateway”本地部署模式（前端 `12881` + gateway `12882`）。
- 2026-01-18：移除 `community-edge`（Nginx）容器与相关配置，统一采用“前端直连 gateway”本地入口。
- 2026-01-18：移除 API 级自动化回归脚本与 CI job，仓库默认不再提供端到端回归门禁。
- 2026-01-18：`secret-scan` 改为仅扫描 git tracked 文件，避免本地 `deploy/.env` 阻断 `security-check`（仍会阻止 `deploy/.env` 被提交）。
- 2026-01-18：为 Nacos 增加健康检查，并将各服务对 Nacos 的依赖条件改为 `service_healthy`，避免首次启动时服务注册偶发失败（如 `analytics-service` 退出）。
- 2026-01-18：P0 生产可用加固：MySQL 同实例多 schema（非身份域先拆）+ 备份/恢复支持多库 + DLQ 指标/告警与回放脚本。
- 2026-01-20：Kafka 增加 healthcheck + `restart: on-failure`，并将依赖 Kafka 的启动条件改为等待 `service_healthy`，修复首次启动偶发 NodeExists 导致的 Kafka/业务服务退出。
- 2026-01-20：docker compose project name 固定为 `community`；Nacos 控制台端口默认绑定到宿主机 `127.0.0.1:${NACOS_UI_PORT:-8848}`，便于通过 UI 修改配置。
