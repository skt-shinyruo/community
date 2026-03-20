# im-load

自研 IM 压测/长连稳定性工具（链路覆盖 `project-gateway` -> `im-realtime` / `im-core`，也支持在回滚 / 排障时直连 IM 服务）。

覆盖目标（最小可用版）：
- 大量 WebSocket 长连接在线（连接/鉴权/保活）
- 私信链路压测（WS 发送 → Kafka command → `im-core` 落库 → Kafka event → `im-realtime` 推送）
- 慢连接模拟（客户端不读/慢读，触发服务端回压/断连保护）
- 断线重连 + backfill（通过 `im-core` HTTP 拉取历史补齐）

> 说明：单机很难跑到 10 万长连；本工具支持按 userId 范围切分，多机/多进程水平扩展。

---

## 前置

1) 启动服务（推荐 docker compose，暴露 `project-gateway` 与 IM 直连端口）：

```bash
cp deploy/.env.example deploy/.env
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

推荐入口：

- 外部 WebSocket：`ws://localhost:12880/ws/im`
- 外部 HTTP：`http://localhost:12880/api/im/**`
- 直连回滚 / 排障：`ws://localhost:18081/internal/ws/im`、`http://localhost:18082`

2) 确保压测端拿到相同的 JWT secret（与服务端一致）：

```bash
export JWT_HMAC_SECRET='dev-secret-please-change-at-least-32bytes'
```

3) 安装依赖：

```bash
cd tools/im-load
npm install
```

---

## 模式一：connect-only（只测长连 + 鉴权）

以下示例统一显式传 `project-gateway` 地址，避免落到工具当前的直连默认值。

```bash
node src/index.mjs connect-only \
  --wsUrl ws://localhost:12880/ws/im \
  --connections 20000 \
  --startUserId 1 \
  --durationSec 600 \
  --slowConsumerPct 5
```

含义：
- `--connections`：连接数（建议从 1k/5k/10k 逐步爬升）
- `--slowConsumerPct`：随机挑选百分比连接模拟慢消费（暂停 socket read，best-effort）

---

## 模式二：private（私信链路，带断线补拉）

```bash
node src/index.mjs private \
  --wsUrl ws://localhost:12880/ws/im \
  --coreBaseUrl http://localhost:12880 \
  --connections 2000 \
  --startUserId 1 \
  --durationSec 600 \
  --sendPerConnPerSec 0.2 \
  --reconnectEverySec 60 \
  --slowConsumerPct 1
```

行为：
- 每个用户与“下一个 userId”组成 1v1（环状配对），持续发送文本消息（内容内带发送时间戳）
- 统计 `privateMessage` 推送延迟（粗略直方图）
- 每隔 `--reconnectEverySec` 秒，随机关闭一部分连接并重连
- 重连后调用 `im-core` 的 history API 进行 backfill（按 lastSeq 补拉）

---

## 多机/多进程建议

按 userId 范围切片启动多个进程，例如两台机器各 5 万连接：

机器 A：
```bash
node src/index.mjs connect-only --wsUrl ws://localhost:12880/ws/im --connections 50000 --startUserId 1 --durationSec 600
```

机器 B：
```bash
node src/index.mjs connect-only --wsUrl ws://localhost:12880/ws/im --connections 50000 --startUserId 50001 --durationSec 600
```

---

## 常用参数

- `--jwtSecret`：不传则读取环境变量 `JWT_HMAC_SECRET`
- `--wsUrl`：WebSocket 地址。外部客户端推荐 `ws://localhost:12880/ws/im`；当前内置默认值也已切到该地址。若要直连 worker 调试，请显式传 `ws://localhost:18081/internal/ws/im`
- `--coreBaseUrl`：HTTP 基地址。外部客户端推荐 `http://localhost:12880`（访问 `/api/im/**`）；当前内置默认值也已切到该地址。若要直连 `im-core` 调试，请显式传 `http://localhost:18082`
- `--durationSec`：持续时间（默认 600 秒）
- `--connections`：连接数
- `--startUserId`：起始 userId（用于切分压测负载）
- `--sendPerConnPerSec`：每连接平均发送速率（小数）
- `--reconnectEverySec`：重连周期（0 表示不重连）
- `--slowConsumerPct`：慢消费者百分比（0-100）
