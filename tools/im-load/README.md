# im-load

自研 IM 压测/长连稳定性工具。当前版本仍是旧协议工具，不覆盖 `community-im-gateway` 的 session-bootstrap 入口。

## 当前兼容性

该工具当前仍使用旧 `auth` 首帧协议，并直接连接传入的 `--wsUrl`：

```text
WebSocket <legacy-compatible-ws-url>
  -> send {"type":"auth","accessToken":"..."}
  -> expect auth_ok / auth_error
```

当前浏览器客户端的真实协议已经变为：

```text
POST /api/im/sessions
  -> response wsUrl + ticket
  -> WebSocket wsUrl
  -> send {"type":"connect","ticket":"..."}
```

因此本工具只能用于旧协议兼容性、实验环境或升级工具前的粗略连接压力参考，不能代表当前 IM session-bootstrap 生产语义。当前 single / cluster 全栈拓扑中的 `ws://localhost:12880/ws/im` 已进入 `community-im-gateway`，会要求 `connect(ticket)`。需要压测当前语义时，先升级 `tools/im-load/src/index.mjs`，让它按 `/api/im/sessions` 获取 `wsUrl` 和 ticket 后再建连。

## 前置

推荐先起单机开发拓扑：

```bash
cp deploy/.env.single.example deploy/.env.single
./deploy/deployment.sh up --topology single
```

如果你要压集群栈，把上面的命令换成：

```bash
cp deploy/.env.cluster.example deploy/.env.cluster
./deploy/deployment.sh up --topology cluster
```

JWT secret 仍需与服务端一致：

```bash
export JWT_HMAC_SECRET='dev-local-jwt-hmac-secret-change-before-sharing-20260320'
```

安装依赖：

```bash
cd tools/im-load
npm install
```

## connect-only

```bash
node src/index.mjs connect-only \
  --wsUrl ws://legacy-im-edge.example/ws/im \
  --connections 20000 \
  --startUserId 1 \
  --durationSec 600 \
  --slowConsumerPct 5
```

## private

```bash
node src/index.mjs private \
  --wsUrl ws://legacy-im-edge.example/ws/im \
  --coreBaseUrl http://localhost:12880 \
  --connections 2000 \
  --startUserId 1 \
  --durationSec 600 \
  --sendPerConnPerSec 0.2 \
  --reconnectEverySec 60 \
  --slowConsumerPct 1
```

## 常用参数

- `--jwtSecret`
- `--wsUrl`：旧协议兼容 WS 地址；代码默认值仍是 `ws://localhost:12880/ws/im`，但当前 full compose 下该地址已经是新 IM edge，不适合本工具
- `--coreBaseUrl`
- `--durationSec`
- `--connections`
- `--startUserId`
- `--sendPerConnPerSec`
- `--reconnectEverySec`
- `--slowConsumerPct`
