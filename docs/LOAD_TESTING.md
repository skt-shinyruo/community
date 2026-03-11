# Load Testing

本仓库的 IM（`im-realtime` / `im-core`）在设计上目标是支撑**大量 WebSocket 长连接在线**与**峰值期间文本消息写入**，正确性依赖 “WS best-effort 推送 + 断线补拉（HTTP）”。

## 工具

自研压测工具：`tools/im-load/`

- `connect-only`：只测长连与鉴权（适合做 100k online 的容量压测）
- `private`：私信链路压测（WS → Kafka → 落库 → Kafka → WS），包含断线重连 + backfill

具体用法见：`tools/im-load/README.md`

## 推荐压测分层

1) **长连容量（首要）**
- 目标：连接数、内存、CPU、GC、连接稳定性
- 模式：`connect-only`

2) **写入链路（私信）**
- 目标：`im-core` 落库吞吐与延迟、Kafka backplane、`im-realtime` 推送延迟
- 模式：`private`

3) **慢连接 / 回压**
- 目标：慢消费者不拖垮整体；触发服务端断连保护；队列不无限增长
- 模式：`connect-only --slowConsumerPct ...` 或 `private --slowConsumerPct ...`

4) **断线补拉**
- 目标：断线后通过 `im-core` history API 补齐（正确性路径）
- 模式：`private --reconnectEverySec ...`

## 本地启动（暴露 IM 端口）

```bash
cp deploy/.env.example deploy/.env
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

端口：
- `im-realtime`：`ws://localhost:18081/ws/im`
- `im-core`：`http://localhost:18082`
