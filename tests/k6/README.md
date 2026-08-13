# k6 负载测试套件

本套件面向网关优先的本地拓扑。默认将流量发送到 `http://localhost:12880`，而不是直接发送到 `community-app`。

## 启动目标服务栈

在仓库根目录执行：

```bash
cp deploy/stacks/cluster/.env.example deploy/stacks/cluster/.env
./deploy/deployment.sh up --stack cluster
export K6_BASE_URL=http://localhost:13880
```

如需进行规模更小的测试，也可以使用 `single`：

```bash
cp deploy/stacks/single/.env.example deploy/stacks/single/.env
./deploy/deployment.sh up --stack single
```

## 运行

运行器使用 `docker run grafana/k6`，因此不要求本地安装 k6 二进制文件。

```bash
cd tests/k6
npm run smoke
npm run api-mix
npm run write-paths
npm run im-ws
npm run soak
npm run stress
npm run spike
```

结果会导出到 `temp/k6-results`。

## 热路径场景

执行：

```bash
npm run hot-path
```

该场景始终调用 `/api/feed/global?size=<K6_READ_SIZE>`。
设置 `K6_BOARD_ID=<uuid>` 后会包含 `/api/boards/{boardId}/feed`。
设置 `K6_POST_ID=<uuid>` 后会包含 `/api/posts/{postId}`。

可在预热运行以及 Redis 刷新或重启演练后使用该场景，通过
`community_cache_requests_total` 对比命中、回退、降级和 single-flight 行为。

## 数据和账号

本地种子数据包含已激活用户。默认账号如下：

- 主账号：`aaa / aaa`
- 次账号：`bbb / aaa`
- 管理员：`admin / aaa`

可以通过以下方式覆盖默认账号：

```bash
K6_USERNAME=aaa K6_PASSWORD=aaa npm run smoke
```

常用环境变量：

- `K6_BASE_URL`：网关地址，默认为 `http://localhost:12880`。
- `K6_WS_URL`：WebSocket 地址，默认根据网关地址派生为 `/ws/im`。
- `K6_DOCKER_IMAGE`：运行器使用的 k6 镜像，默认为 `grafana/k6:0.51.0`。
- `K6_WRITE_RATIO`：`write-paths` 中执行写入流程的迭代比例，默认为 `10`。
- `K6_BOARD_ID`：`hot-path` 使用的可选版块 UUID。
- `K6_POST_ID`：`hot-path` 使用的可选帖子 UUID。
- `K6_ALLOW_WRITES`：设置为 `false` 可禁用写入操作。
- `K6_IM_HOLD_SECONDS`：每个 WebSocket 连接的保持时间，默认为 `20`。
- `K6_IM_SEND_MESSAGES`：设置为 `true` 可通过 WebSocket 发送房间消息。
- `K6_IM_ROOM_ID`：当 `K6_IM_SEND_MESSAGES=true` 时必填。
- `K6_HTTP_FAILED_RATE`、`K6_HTTP_P95_MS`、`K6_HTTP_P99_MS`、`K6_CHECK_RATE`：阈值控制参数。

## 测试配置

- `smoke`：低流量健康检查、公开读取、登录和鉴权探测。
- `api-mix`：对内容、搜索、市场、云盘、通知、钱包和 IM 历史 API 发起混合的公开及鉴权读取流量。
- `write-paths`：低速率有状态写入，包括发帖、评论、收藏、点赞和创建云盘文件夹。
- `im-ws`：打开 `/api/im/sessions`，连接 `/ws/im`，发送 `connect` 和定期 `ping` 帧。
- `soak`：长时间运行混合读取，用于检查内存泄漏和队列堆积。
- `stress`：逐步提升到达速率，用于发现系统饱和点。
- `spike`：突然增加流量，用于观察恢复能力和限流行为。

## 阈值

默认阈值目标：

- `http_req_failed` 低于 `1%`。
- API `http_req_duration` 的 p95 低于 `800ms`，p99 低于 `1500ms`。
- `checks` 高于 `98%`。
- WebSocket 连接 p95 低于 `1000ms`。
- 不允许登录失败。

可以通过设置 `K6_HTTP_FAILED_RATE`、`K6_HTTP_P95_MS`、`K6_HTTP_P99_MS` 和 `K6_CHECK_RATE` 调整阈值。

## 可观测性

运行负载测试时请使用可观测性配置。
该配置默认启用；只有在明确需要禁用时才添加 `--no-observability`。

- 网关和各服务暴露 `/actuator/prometheus`。
- 启用可观测性后，可通过 `http://localhost:12889` 访问 Kibana。
- Elasticsearch 可通过 `http://localhost:12888` 访问。

请关注 p95/p99 延迟、HTTP 错误率、JVM 堆和 GC、数据库连接池、Redis 延迟、Kafka 延迟、outbox 积压、Elasticsearch 查询延迟以及网关限流决策。

## 安全提示

`write-paths` 会创建真实的本地记录。除非已有生产环境安全的数据和清理方案，否则请将其指向本地或可丢弃环境。默认套件不包含破坏性删除操作；除非添加了明确的清理场景，否则请保持 `K6_ALLOW_DESTRUCTIVE_WRITES=false`。
