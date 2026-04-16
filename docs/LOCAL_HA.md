# 本地 HA 演练手册

本页只描述 `ha` 拓扑。

## 1. 启动

```bash
cp deploy/.env.ha.example deploy/.env.ha
./deploy/deployment.sh up --topology ha
```

如果需要 observability：

```bash
./deploy/deployment.sh up --topology ha --observability
```

## 2. 关键服务

- `community-gateway-1..3`
- `community-app-1..3`
- `im-core-1..3`
- `im-realtime-1..3`
- `nacos-1..3`
- `xxl-job-admin-1..2`
- `mysql-primary` / `mysql-replica-1/2`
- `redis-1..6`
- `kafka-1..3`
- `elasticsearch-1..3`

## 3. 常用命令

查看状态：

```bash
./deploy/deployment.sh ps --topology ha
```

查看网关日志：

```bash
./deploy/deployment.sh logs --topology ha community-gateway-1
```

渲染最终配置：

```bash
./deploy/deployment.sh config --topology ha
```

停止单个服务演练：

```bash
docker compose \
  --env-file deploy/.env.ha \
  -p community-ha \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.ha.yml \
  -f deploy/compose.infra.redis.ha.yml \
  -f deploy/compose.infra.kafka.ha.yml \
  -f deploy/compose.infra.elasticsearch.ha.yml \
  -f deploy/compose.infra.nacos.ha.yml \
  -f deploy/compose.infra.xxl-job.ha.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.ha.yml \
  -f deploy/compose.runtime.services.ha.yml \
  -f deploy/compose.runtime.frontend-nginx.ha.yml \
  -f deploy/compose.runtime.mock-data-studio.ha.yml \
  stop community-gateway-1
```

重启该服务：

```bash
docker compose \
  --env-file deploy/.env.ha \
  -p community-ha \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.ha.yml \
  -f deploy/compose.infra.redis.ha.yml \
  -f deploy/compose.infra.kafka.ha.yml \
  -f deploy/compose.infra.elasticsearch.ha.yml \
  -f deploy/compose.infra.nacos.ha.yml \
  -f deploy/compose.infra.xxl-job.ha.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.ha.yml \
  -f deploy/compose.runtime.services.ha.yml \
  -f deploy/compose.runtime.frontend-nginx.ha.yml \
  -f deploy/compose.runtime.mock-data-studio.ha.yml \
  up -d community-gateway-1
```

## 4. 常见检查

Nacos worker 列表：

```bash
curl -fsS "http://localhost:18848/nacos/v1/ns/instance/list?serviceName=im-realtime-worker"
```

网关 502：

- 先看 `./deploy/deployment.sh ps --topology ha`
- 再看 `./deploy/deployment.sh logs --topology ha community-gateway-1`
- 再看 `./deploy/deployment.sh logs --topology ha im-realtime-1`

Kafka 长时间 `health: starting`：

- `./deploy/deployment.sh logs --topology ha kafka-1`
- 如果是从旧拓扑切过来，执行 `./deploy/deployment.sh down --topology ha -v` 后重启

## 5. 停止与重置

```bash
./deploy/deployment.sh down --topology ha
./deploy/deployment.sh down --topology ha -v
```
