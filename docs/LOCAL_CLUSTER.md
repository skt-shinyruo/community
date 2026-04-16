# 本地集群演练手册

本页只描述 `cluster` 拓扑。

## 1. 启动

```bash
cp deploy/.env.cluster.example deploy/.env.cluster
./deploy/deployment.sh up --topology cluster
```

如果需要 observability：

```bash
./deploy/deployment.sh up --topology cluster --observability
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
./deploy/deployment.sh ps --topology cluster
```

查看网关日志：

```bash
./deploy/deployment.sh logs --topology cluster community-gateway-1
```

渲染最终配置：

```bash
./deploy/deployment.sh config --topology cluster
```

停止单个服务演练：

```bash
docker compose \
  --env-file deploy/.env.cluster \
  -p community-cluster \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.cluster.yml \
  -f deploy/compose.infra.redis.cluster.yml \
  -f deploy/compose.infra.kafka.cluster.yml \
  -f deploy/compose.infra.elasticsearch.cluster.yml \
  -f deploy/compose.infra.nacos.cluster.yml \
  -f deploy/compose.infra.xxl-job.cluster.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.cluster.yml \
  -f deploy/compose.runtime.services.cluster.yml \
  -f deploy/compose.runtime.frontend-nginx.cluster.yml \
  -f deploy/compose.runtime.mock-data-studio.cluster.yml \
  stop community-gateway-1
```

重启该服务：

```bash
docker compose \
  --env-file deploy/.env.cluster \
  -p community-cluster \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.cluster.yml \
  -f deploy/compose.infra.redis.cluster.yml \
  -f deploy/compose.infra.kafka.cluster.yml \
  -f deploy/compose.infra.elasticsearch.cluster.yml \
  -f deploy/compose.infra.nacos.cluster.yml \
  -f deploy/compose.infra.xxl-job.cluster.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.cluster.yml \
  -f deploy/compose.runtime.services.cluster.yml \
  -f deploy/compose.runtime.frontend-nginx.cluster.yml \
  -f deploy/compose.runtime.mock-data-studio.cluster.yml \
  up -d community-gateway-1
```

## 4. 常见检查

Nacos worker 列表：

```bash
curl -fsS "http://localhost:18848/nacos/v1/ns/instance/list?serviceName=im-realtime-worker"
```

网关 502：

- 先看 `./deploy/deployment.sh ps --topology cluster`
- 再看 `./deploy/deployment.sh logs --topology cluster community-gateway-1`
- 再看 `./deploy/deployment.sh logs --topology cluster im-realtime-1`

Kafka 长时间 `health: starting`：

- `./deploy/deployment.sh logs --topology cluster kafka-1`
- 如果是从旧拓扑切过来，执行 `./deploy/deployment.sh down --topology cluster -v` 后重启

## 5. 停止与重置

```bash
./deploy/deployment.sh down --topology cluster
./deploy/deployment.sh down --topology cluster -v
```
