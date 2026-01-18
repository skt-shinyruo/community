# legacy-community 模块（迁移期单体）

## 1. 职责
- 作为迁移期单体承载旧业务逻辑与 Thymeleaf 页面，后续逐步拆分并下线。

## 2. 迁移期约束
- 已完成 Jakarta 包迁移与 Security 6 适配，保证 Boot 3 + Java 17 下可编译。
- Elasticsearch 旧实现已降级移除（计划在迭代 1 由 `search-service` 重写）。
- Kafka Listener 默认不自动启动（避免本地无 Kafka 时阻塞启动）：`spring.kafka.listener.auto-startup=false`。
- 本仓库当前仅保留源码用于对照与迁移参考，不再将 legacy-community 纳入 docker compose 部署与切流。

## 3. 关键文件
- 启动类：`legacy-community/src/main/java/com/nowcoder/community/CommunityApplication.java`
- 配置：`legacy-community/src/main/resources/application.yml`
