# im-load

自研 IM 压测/长连稳定性工具，默认压 `community-gateway` 对外入口。

## 前置

推荐先起单机开发拓扑：

```bash
cp deploy/.env.dev.example deploy/.env.dev
./deploy/deployment.sh up --topology dev
```

如果你要压 HA 栈，把上面的命令换成：

```bash
cp deploy/.env.ha.example deploy/.env.ha
./deploy/deployment.sh up --topology ha
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
  --wsUrl ws://localhost:12880/ws/im \
  --connections 20000 \
  --startUserId 1 \
  --durationSec 600 \
  --slowConsumerPct 5
```

## private

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

## 常用参数

- `--jwtSecret`
- `--wsUrl`
- `--coreBaseUrl`
- `--durationSec`
- `--connections`
- `--startUserId`
- `--sendPerConnPerSec`
- `--reconnectEverySec`
- `--slowConsumerPct`
