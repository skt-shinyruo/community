# 本地 HA 演练手册

> 约定：命令默认在仓库根目录执行，环境文件使用 `deploy/.env`。若你只想复现仓库默认值，可先执行 `cp deploy/.env.example deploy/.env`。

## 1. 目标与边界

这套本地 HA 栈的目标是演练：

- `NGINX` 后面的业务服务多副本行为
- MySQL / Redis / Kafka / Elasticsearch 的多节点形态
- `xxl-job-admin` 双副本控制面
- 单个业务实例或单个中间件节点故障后的可用性

明确不覆盖：

- `frontend` 多副本
- `NGINX` 自身的无感高可用
- MySQL 自动无感写切主
- MailHog / `mock-data-studio` / bootstrap sidecar / observability 组件的 HA

## 2. 资源建议

- 建议至少 `16GB` 内存
- 建议至少 `8` 个逻辑核
- 首次 `--build`、Kafka topic 初始化、Redis Cluster 组网、MySQL 主从收敛都会明显慢于旧单节点 compose

## 3. 启动

```bash
cp deploy/.env.example deploy/.env
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  up -d --build
```

默认入口：

- 前端：`http://localhost:12881`
- 业务入口：`http://localhost:12880`
- Nacos 注册检查：`http://localhost:18848/nacos`
- XXL-JOB Admin：`http://localhost:12887/xxl-job-admin`

## 4. 健康检查

### 4.1 关键容器状态

```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  ps
```

重点观察：

- `community-gateway-1..3`
- `community-app-1..3`
- `im-core-1..3`
- `im-realtime-1..3`
- `mysql-primary` / `mysql-replica-1/2`
- `redis-1..6`
- `kafka-1..3`
- `elasticsearch-1..3`
- `nginx`

### 4.2 入口检查

```bash
curl -fsS http://localhost:12880/actuator/health
curl -fsS -I http://localhost:12887/xxl-job-admin/
```

期望：

- 第一个返回 `{"status":"UP"}`
- 第二个通常返回 `302`，跳转到 `/xxl-job-admin/auth/login`

### 4.3 Nacos 注册检查

`localhost:18848` 仅映射到 `nacos-1` 作为 operator 检查入口；业务容器默认连接 `nacos-1:8848,nacos-2:8848,nacos-3:8848`。

```bash
curl -fsS "http://localhost:18848/nacos/v1/ns/instance/list?serviceName=community-app"
curl -fsS "http://localhost:18848/nacos/v1/ns/instance/list?serviceName=im-core"
curl -fsS "http://localhost:18848/nacos/v1/ns/instance/list?serviceName=im-realtime-worker"
```

期望：

- 每个响应都包含非空 `hosts`
- `community-app`、`im-core`、`im-realtime-worker` 都已完成注册

## 5. 故障演练

### 5.1 Gateway 单实例故障

```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  stop community-gateway-1
curl -fsS http://localhost:12880/actuator/health
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  up -d community-gateway-1
```

期望：

- 入口健康检查仍返回 `UP`
- `NGINX` 自动转发到剩余 gateway 副本

### 5.2 community-app 单实例故障

```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  stop community-app-1
curl -fsS "http://localhost:12880/api/posts?order=latest&page=0&size=1"
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  up -d community-app-1
```

期望：

- gateway HTTP upstream 池切到 `community-app-2/3`
- 已存在的应用内本地状态不会共享；但典型读路径仍可用

### 5.3 im-realtime worker 故障

```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  stop im-realtime-1
curl -fsS "http://localhost:18848/nacos/v1/ns/instance/list?serviceName=im-realtime-worker"
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  up -d im-realtime-1
```

期望：

- 停掉 worker 上承载的现存 WebSocket 连接会断开
- 浏览器重连后会被 gateway 重新分配到剩余 worker
- Nacos 中 `im-realtime-worker` 的实例列表会先缩容，恢复后再回补
- 新消息扇出仍可继续通过 Kafka 事件和剩余 worker 完成

### 5.4 Redis 单节点故障

```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  stop redis-1
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  up -d redis-1
```

期望：

- Redis Cluster 维持可用，但会有 slot 迁移/副本提升窗口
- 若同时故障多个相关主从节点，业务可能出现短暂失败

### 5.5 Kafka 单 broker 故障

```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  stop kafka-1
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  up -d kafka-1
```

期望：

- 由于 topic replication factor 为 `3`、min ISR 为 `2`，单 broker 故障下 IM command / event topic 仍应可用
- `kafka-init` 无需重复执行；topic 元数据会由集群保留

### 5.6 Elasticsearch 单节点故障

```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  stop elasticsearch-1
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  up -d elasticsearch-1
```

期望：

- 集群仍可进入 `yellow` 或 `green`，搜索能力应继续可用
- 若你同时跑了 `observability-elastic`，Kibana/collector 会在节点恢复后继续使用别名入口

### 5.7 XXL-JOB Admin 单实例故障

```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  stop xxl-job-admin-1
curl -fsS -I http://localhost:12887/xxl-job-admin/
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  up -d xxl-job-admin-1
```

期望：

- `NGINX :12887` 仍返回登录入口
- 管理平面仍可由剩余 admin 副本承接

## 6. MySQL 人工切主

这套栈只保证 MySQL 的“多副本 + 可人工切主”，不保证自动写切换。

### 6.1 提升副本

假设你要把 `mysql-replica-1` 提升为新主：

```bash
docker exec community-mysql-replica-1-1 \
  mysql -uroot -p'<mysql-root-password>' \
  -e "stop replica; reset replica all; set global read_only = off; set global super_read_only = off;"
```

### 6.2 重新指向写主

修改 `deploy/.env`：

```dotenv
DB_PRIMARY_HOST=mysql-replica-1
IM_CORE_DB_PRIMARY_HOST=mysql-replica-1
```

然后重建会直连写主的服务：

```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  up -d --force-recreate \
  community-app-1 community-app-2 community-app-3 \
  im-core-1 im-core-2 im-core-3 \
  xxl-job-admin-1 xxl-job-admin-2 \
  mysql-replication-bootstrap
```

### 6.3 重新挂载剩余副本

保留的旧副本重新接入新主后，检查：

```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  logs --tail=120 mysql-replication-bootstrap
```

期望：

- `Replica_IO_Running: Yes`
- `Replica_SQL_Running: Yes`

## 7. 常见排障

- `curl :12880` 返回 `502`：先看 `docker compose -f deploy/compose.yml -f deploy/compose.infra.mysql.yml -f deploy/compose.infra.redis.yml -f deploy/compose.infra.kafka.yml -f deploy/compose.infra.elasticsearch.yml -f deploy/compose.infra.nacos.yml -f deploy/compose.infra.xxl-job.yml -f deploy/compose.infra.mailhog.yml -f deploy/compose.infra.mock-data-studio-bootstrap.yml -f deploy/compose.runtime.yml --env-file deploy/.env ps --all community-gateway-1 community-gateway-2 community-gateway-3`
- `gateway` 没起来：看 `docker compose -f deploy/compose.yml -f deploy/compose.infra.mysql.yml -f deploy/compose.infra.redis.yml -f deploy/compose.infra.kafka.yml -f deploy/compose.infra.elasticsearch.yml -f deploy/compose.infra.nacos.yml -f deploy/compose.infra.xxl-job.yml -f deploy/compose.infra.mailhog.yml -f deploy/compose.infra.mock-data-studio-bootstrap.yml -f deploy/compose.runtime.yml --env-file deploy/.env logs --tail=200 community-gateway-1`
- `im-realtime` 没起来：看 `docker compose -f deploy/compose.yml -f deploy/compose.infra.mysql.yml -f deploy/compose.infra.redis.yml -f deploy/compose.infra.kafka.yml -f deploy/compose.infra.elasticsearch.yml -f deploy/compose.infra.nacos.yml -f deploy/compose.infra.xxl-job.yml -f deploy/compose.infra.mailhog.yml -f deploy/compose.infra.mock-data-studio-bootstrap.yml -f deploy/compose.runtime.yml --env-file deploy/.env logs --tail=200 im-realtime-1`
- `Kafka` 卡在 `health: starting`：先看 `docker compose -f deploy/compose.yml -f deploy/compose.infra.mysql.yml -f deploy/compose.infra.redis.yml -f deploy/compose.infra.kafka.yml -f deploy/compose.infra.elasticsearch.yml -f deploy/compose.infra.nacos.yml -f deploy/compose.infra.xxl-job.yml -f deploy/compose.infra.mailhog.yml -f deploy/compose.infra.mock-data-studio-bootstrap.yml -f deploy/compose.runtime.yml --env-file deploy/.env logs kafka-1` 是否卡在 controller quorum / metadata log 初始化；如果是从旧 ZooKeeper 栈切过来，先执行 `docker compose -f deploy/compose.yml -f deploy/compose.infra.mysql.yml -f deploy/compose.infra.redis.yml -f deploy/compose.infra.kafka.yml -f deploy/compose.infra.elasticsearch.yml -f deploy/compose.infra.nacos.yml -f deploy/compose.infra.xxl-job.yml -f deploy/compose.infra.mailhog.yml -f deploy/compose.infra.mock-data-studio-bootstrap.yml -f deploy/compose.runtime.yml --env-file deploy/.env down -v` 清掉旧的 `kafka_*` 数据卷再重启
- `mysql-replication-bootstrap` 失败：先确认三个 MySQL 节点都 `healthy`，再看 sidecar 日志里的 `Last_IO_Error` / `Last_SQL_Error`

## 8. 清理

停止：

```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  down
```

完全清理（包含数据卷，谨慎）：

```bash
docker compose \
  -f deploy/compose.yml \
  -f deploy/compose.infra.mysql.yml \
  -f deploy/compose.infra.redis.yml \
  -f deploy/compose.infra.kafka.yml \
  -f deploy/compose.infra.elasticsearch.yml \
  -f deploy/compose.infra.nacos.yml \
  -f deploy/compose.infra.xxl-job.yml \
  -f deploy/compose.infra.mailhog.yml \
  -f deploy/compose.infra.mock-data-studio-bootstrap.yml \
  -f deploy/compose.runtime.yml \
  --env-file deploy/.env \
  down -v
```
