# Task List: Kafka ClusterId 不一致问题长期修复（持久化 Zookeeper）

Directory: `helloagents/history/2026-01/202601172340_kafka_clusterid_persist_zookeeper/`

---

## 1. 根因修复：Zookeeper 数据持久化
- [√] 1.1 为 `deploy/docker-compose.yml` 的 `zookeeper` 服务增加 data/log 的 named volumes（避免 `docker compose down` 后 ZK 状态丢失导致 Kafka clusterId 不一致）
- [√] 1.2 在 compose `volumes:` 区域登记新增的 zookeeper volumes

## 2. 文档与排障同步
- [√] 2.1 更新运行手册：补充 “Kafka InconsistentClusterIdException” 排障与一次性修复步骤（清理相应 volume）
- [√] 2.2 更新 infra 模块说明与 CHANGELOG：记录该修复

## 3. 验证
- [√] 3.1 `docker compose config -q` 通过
