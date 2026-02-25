# 运行手册：docker compose 启动与回归（不含 legacy）

> 目标：通过 docker compose 拉起“微服务全链路 + 全依赖”，并用自动化回归验证功能完备性。
>
> 说明：历史单体模块已从仓库主干移除；本仓库以微服务 + SPA 为唯一运行路径，不提供切流/回滚脚本化入口。

---

## 1. 启动前准备

1. 环境要求：
   - Docker / docker compose 可用
   - 本机可用端口：`12881`（frontend）与 `12882`（gateway）
   - 说明：内部依赖组件（Nacos/MySQL/Redis/Kafka/Elasticsearch/观测栈）默认不再映射到宿主机端口，以避免与本机已安装服务（如 Redis 6379）冲突
2. 准备环境变量：
   - 复制 `deploy/.env.example` 为 `deploy/.env`
   - 确认 `JWT_HMAC_SECRET` 长度 >= 32 字节（auth-service 签发、gateway/资源服务验签需一致）

---

## 2. 启动微服务全栈（compose）

在仓库根目录执行：

- 启动（含观测栈）：
  - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env up -d --build`
- （可选）开启观测/日志端口映射（Grafana/Loki/Prometheus/Alertmanager，端口 `12883+`）：
  - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml -f deploy/docker-compose.ports.yml --env-file deploy/.env up -d --build`
- 查看状态：
  - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env ps`
- 查看日志（示例）：
  - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env logs -f gateway`

启动完成后：
- 浏览器访问前端：`http://localhost:12881`
- API 直连网关：`http://localhost:12882`

---

## 3. 功能验证（推荐）

本仓库不再内置“API 级端到端自动回归脚本”。建议采用以下方式验证功能完备性：

- CI（推荐）：`mvn test`（后端）+ `npm -C frontend test`（前端）
- 本地手工验收：访问 `http://localhost:12881`，使用 `aaa/aaa` 登录并完成“发帖/评论/点赞/关注/私信/通知/搜索”等核心路径

---

## 4. 停止与清理

- 停止并保留数据卷（保留 MySQL/Redis/ES 数据，用于复现）：
  - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env down`
- 停止并清理数据卷（完全重置）：
  - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env down -v`

---

## 5. 常见问题（Troubleshooting）

### 5.1 Kafka 启动失败：InconsistentClusterIdException

现象：
- `community-kafka` 退出（`Exited (1)`），日志包含 `kafka.common.InconsistentClusterIdException`
- 依赖 Kafka 的服务（如 message/content/search）可能随之启动失败（Kafka consumer 初始化失败）

原因：
- Kafka 的数据卷（`kafka_data`）保留了历史 `meta.properties`（clusterId）
- 但 Zookeeper 状态被重建（例如之前未持久化 ZK、或手动清理过 ZK 数据），导致两边 clusterId 不一致

一次性修复（开发/测试推荐，清空 Kafka 状态）：
1. 先停止：
   - `docker compose -f deploy/docker-compose.yml --env-file deploy/.env down`
2. 删除 Kafka + Zookeeper 数据卷（会丢 Kafka topic/message）：
   - `docker volume rm deploy_kafka_data deploy_zookeeper_data deploy_zookeeper_log`
3. 再启动：
   - `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`
