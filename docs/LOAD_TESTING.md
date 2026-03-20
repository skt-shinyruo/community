# Load Testing

本仓库的 IM（`im-realtime` / `im-core`）在设计上目标是支撑**大量 WebSocket 长连接在线**与**峰值期间文本消息写入**，正确性依赖 “WS best-effort 推送 + 断线补拉（HTTP）”。

对外客户端与压测流量，当前推荐统一通过 `community-gateway` 的 `http://localhost:12880` 进入 IM：

- WebSocket：`ws://localhost:12880/ws/im`
- HTTP：`http://localhost:12880/api/im/**`

`ws://localhost:18081/internal/ws/im` 与 `http://localhost:18082` 仍保留为回滚 / 排障时的直连路径，但默认不暴露；需要时通过 `debug` profile 开启。

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

## 本地启动（推荐通过 community-gateway 暴露 IM 入口）

```bash
cp deploy/.env.example deploy/.env
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

端口：
- `community-gateway`：`http://localhost:12880`
  - 外部 WebSocket 入口：`ws://localhost:12880/ws/im`
  - 外部 HTTP 入口：`http://localhost:12880/api/im/**`
- 若需要直连排障口，请额外开启：
  - `COMPOSE_PROFILES=debug docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d`
  - `im-realtime` 直连：`ws://localhost:18081/internal/ws/im`
  - `im-core` 直连：`http://localhost:18082`
