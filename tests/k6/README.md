# k6 负载测试套件

本套件通过 Gateway 测试已部署系统，默认目标是 `http://localhost:12880`。场景语义、验收阈值和观测方法以[压测指南](../../docs/handbook/performance-testing.md)为准。

## 启动目标 Stack

推荐使用 cluster：

```bash
cp deploy/stacks/cluster/.env.example deploy/stacks/cluster/.env
./deploy/deployment.sh up --stack cluster
export K6_BASE_URL=http://localhost:13880
```

轻量测试可使用 single；需要 Kibana 和 tracing 时显式开启观测层：

```bash
cp deploy/stacks/single/.env.example deploy/stacks/single/.env
./deploy/deployment.sh up --stack single --observability
```

## 运行

运行器使用 `docker run grafana/k6`，无需在宿主机安装 k6：

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

结果写入 `temp/k6-results`。

场景用途：

- `smoke`：低流量健康、公开读、登录和鉴权探测
- `api-mix`：内容、搜索、市场、云盘、通知、钱包和 IM 历史混合读取
- `hot-path`：global/board feed 与 post detail 缓存路径
- `write-paths`：低速率发帖、评论、收藏、点赞和 Drive 文件夹写入
- `im-ws`：IM session、WebSocket 建连、`connect` 和 `ping`
- `soak`、`stress`、`spike`：长稳、饱和点和突发恢复

## 配置

本地种子账号为 `aaa / aaa`、`bbb / aaa`、`admin / aaa`。常用覆盖项：

- `K6_BASE_URL`：Gateway 地址，默认 `http://localhost:12880`
- `K6_WS_URL`：WebSocket 地址，默认从 Gateway 派生 `/ws/im`
- `K6_USERNAME`、`K6_PASSWORD`：登录账号
- `K6_DOCKER_IMAGE`：runner 镜像，默认 `grafana/k6:0.51.0`
- `K6_BOARD_ID`、`K6_POST_ID`：`hot-path` 的可选实体
- `K6_WRITE_RATIO`、`K6_ALLOW_WRITES`：写入比例和总开关
- `K6_IM_HOLD_SECONDS`、`K6_IM_SEND_MESSAGES`、`K6_IM_ROOM_ID`：IM 场景参数
- `K6_HTTP_FAILED_RATE`、`K6_HTTP_P95_MS`、`K6_HTTP_P99_MS`、`K6_CHECK_RATE`：threshold 覆盖项

例如：

```bash
K6_USERNAME=aaa K6_PASSWORD=aaa npm run smoke
K6_BOARD_ID=<board-uuid> K6_POST_ID=<post-uuid> npm run hot-path
```

## 观测与安全

启用观测层后，可检查 `/actuator/prometheus`、Kibana `http://localhost:12889` 和 Elasticsearch `http://localhost:12888`。应结合 p95/p99、HTTP 错误、JVM、连接池、Redis、Kafka、outbox 和搜索延迟判断结果，不能把本地 threshold 当作生产容量承诺。

`write-paths` 会创建真实记录。只应指向本地或明确可丢弃的环境；套件默认不执行破坏性删除。
