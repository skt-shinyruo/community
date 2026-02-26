# community

## 项目介绍（现状 / SSOT）
本仓库为一个“讨论社区”微服务工程（Spring Boot 3 + Java 17 + Vue3），默认本地运行模式为“前端直连 gateway”。

模块（以根 `pom.xml` 与 `docs/ARCHITECTURE.md` 为准）：
- `frontend/`：Vue3 SPA（Vite + Router + Pinia + Axios）
- `gateway/`：Spring Cloud Gateway（统一入口 `/api/**`：鉴权/CORS/traceId/审计/限流）
- `auth-service/`：登录/刷新/登出/验证码/注册激活/找回密码（JWT access + refresh cookie）
- `user/user-service/`：用户资料、头像上传（七牛）、成长/榜单等
- `content/content-service/`：帖子/评论、分类/标签、收藏/订阅、内容生命周期
- `social/social-service/`：点赞/关注/拉黑
- `message-service/`：私信/通知（Kafka 消费生成通知）
- `search/search-service/`：Elasticsearch 搜索 + reindex
- `analytics/analytics-service/`：UV/DAU 统计
- `common/`：Result/错误码/traceId/internal client 支撑（headers/错误映射/指标）等公共库

文档（建议从这里开始）：
- `docs/ARCHITECTURE.md`
- `docs/DEPLOYMENT.md`
- `docs/SECURITY.md`
- `docs/DATA_MODEL.md`
- `docs/OBSERVABILITY.md`

## 本地启动（推荐：前端直连 gateway）

1. 准备环境变量：
   - `cp deploy/.env.example deploy/.env`
2. 启动（前端 `12881`，gateway `12882`）：
   - `docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.frontend-direct.yml --env-file deploy/.env up -d --build`
3. 访问：
   - 前端：`http://localhost:12881`
   - API：`http://localhost:12882`

若 `docker compose ... --build` 偶发失败：先重试一次；仍不行再用 `docker builder prune -af` 清理 BuildKit cache 后重来。

## 版本与依赖（以代码/compose 为准）
- Backend：Java 17 + Spring Boot 3.2.6（见根 `pom.xml`）
- Frontend：Vue 3 + Vite（见 `frontend/package.json`）
- Infra（`deploy/docker-compose.yml`）：
  - Nacos：`nacos/nacos-server:v2.3.2`
  - MySQL：`mysql:8.0`
  - Redis：`redis:7-alpine`
  - Kafka/Zookeeper：`confluentinc/cp-kafka:7.6.1` / `confluentinc/cp-zookeeper:7.6.1`
  - Elasticsearch：`elasticsearch:8.12.2`
  - Observability：Prometheus `v2.51.2` / Grafana `10.4.5` / Loki&Promtail `2.9.4` / Alertmanager `0.27.0`

## 注册中心与关键变量（治理收敛）
本项目当前约定为 **单注册中心（Nacos）**：

- Spring Cloud：服务发现/配置中心均使用 Nacos（`NACOS_SERVER_ADDR`，可选 `NACOS_GROUP/NACOS_NAMESPACE/NACOS_USERNAME/NACOS_PASSWORD`）。
- Dubbo：服务间同步调用默认使用 Nacos registry（同 `NACOS_SERVER_ADDR`）。
  - 应急/迁移窗口可显式设置 `DUBBO_REGISTRY_ADDR` 覆盖 Dubbo registry（默认部署编排不注入，避免双栈常态化）。
- Zookeeper：仍保留在 compose 中供 Kafka 使用（不再承担 Dubbo registry）。

验证 checklist（建议按顺序）：
1. `mvn test`（确保依赖/编译/单测通过）
2. `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build`（全依赖+全服务冒烟）
3. Nacos UI（默认 `http://127.0.0.1:${NACOS_UI_PORT:-8848}`）：确认服务实例可见、排障入口统一
4. 验证 1 条 Dubbo 关键调用链（例如 gateway→analytics Dubbo 调用 / 任意跨服务只读 RPC）
5. （应急演练）显式设置 `DUBBO_REGISTRY_ADDR=zookeeper://...` 覆盖 Dubbo registry 并重启一组服务，确认回滚手段可用（稳定后移除该注入）

## 本地演示账号与便捷配置（dev-only）
⚠️ 默认账号/口令、固定验证码等仅用于本地 dev/演示环境；生产环境禁止使用默认口令与 dev-only 开关。  
详见：`docs/DEV_ONLY.md`（包含种子数据来源与冒烟脚本说明）。

## 历史说明（Legacy）
仓库早期版本来自单体社区实现，README 中曾出现 JDK11/MySQL5.7/ES6/Kafka2.3 以及 wkhtmltopdf/Caffeine/Quartz 等描述。
当前仓库已收敛为 Boot3 + Java17 的多模块微服务实现；如发现文档与代码不一致，请以 `docs/` + `deploy/` + 各模块 `application.yml` 为准。

## 其他项目
- 计算机类电子书仓库：https://github.com/cosen1024/awesome-cs-books
- Java 面试题仓库：https://github.com/cosen1024/Java-Interview
