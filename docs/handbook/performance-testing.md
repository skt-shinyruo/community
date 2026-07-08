# 压测指南

本项目压测默认从 `community-gateway` 统一入口进入，即 `http://localhost:12880`。不要把直接打 `community-app` 的结果当作系统容量，因为真实链路包含 Gateway 路由、限流、服务发现、trace、跨服务调用、IM edge、OSS、Kafka 和 outbox。

## 本地启动

推荐使用 cluster 拓扑和观测层：

```bash
cp deploy/.env.cluster.example deploy/.env.cluster
./deploy/deployment.sh up --topology cluster
```

轻量验证可以使用 single：

```bash
cp deploy/.env.single.example deploy/.env.single
./deploy/deployment.sh up --topology single
```

## k6 套件

压测脚本位于：

```text
tests/k6
```

常用命令：

```bash
cd tests/k6
npm test
npm run smoke
npm run api-mix
npm run hot-path
npm run write-paths
npm run im-ws
npm run soak
npm run stress
npm run spike
```

默认账号来自本地种子数据：`aaa / aaa`、`bbb / aaa`、`admin / aaa`。通过 `K6_USERNAME`、`K6_PASSWORD`、`K6_BASE_URL`、`K6_WS_URL` 可覆盖目标环境；通过 `K6_DOCKER_IMAGE` 可覆盖 runner 使用的 k6 镜像。

## Hot Path Capacity

P3 feed/detail cache 优化使用 `hot-path` 场景做本地容量验收：

```bash
cd tests/k6
K6_BOARD_ID=<board-uuid> K6_POST_ID=<post-uuid> npm run hot-path
```

场景固定访问 global hot feed；配置 `K6_BOARD_ID` 后访问 board hot feed，配置 `K6_POST_ID` 后访问 post detail。建议在热 key 预热完成后跑一次 warm-cache 基线，再在 Redis flush / restart 演练后跑一次恢复基线。

本地 cluster 验收门槛：

- hot feed/detail HTTP 错误率 `< 1%`
- hot path p95 `< 800ms`
- hot path p99 `< 1500ms`
- warm-cache 运行中无持续 Hikari pending pressure
- Redis 故障时出现 `degraded` 或 `singleflight_busy` cache outcome，而不是大面积 HTTP failure spike

## 建议流程

1. 先跑 `smoke`，确认健康检查、公开读接口、登录和认证探测都正常。
2. 跑 `api-mix` 建立读多写少的基线，记录 QPS、p95、p99、错误率、CPU、内存、GC、连接池、Redis、Kafka、ES。
3. 跑 `write-paths` 验证发帖、评论、收藏、点赞和 Drive 文件夹写链路。默认低写入比例，避免本地数据膨胀过快。
4. 跑 `im-ws` 验证 `/api/im/sessions`、`/ws/im` 建连、`connect`、`ping/pong`。
5. 跑 `stress` 和 `spike` 找拐点；跑 `soak` 观察长时间资源泄漏、异步积压和日志压力。

## 观测点

- `/actuator/prometheus`
- Kibana：`http://localhost:12889`
- Elasticsearch：`http://localhost:12888`
- Gateway 日志中的路由、限流和错误响应
- MySQL 慢查询和连接池等待
- Redis 慢命令和连接数
- Kafka consumer lag
- outbox 待处理量和重试量
- JVM heap、GC pause、线程数、FD 使用率

## 默认目标

本地目标建议只作为工程基线，不作为生产容量承诺：

- HTTP 错误率 `< 1%`
- 读接口 p95 `< 800ms`
- 写接口 p95 `< 1500ms`
- login failure 为 `0`
- Kafka/outbox 无持续积压
- Old GC 不频繁，heap 无持续上升

生产容量评估必须使用独立压测环境、明确的数据规模、资源规格和清理方案。
