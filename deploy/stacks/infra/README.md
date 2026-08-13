# Infra Stack

独立的单节点基础设施 Stack，供运行在宿主机上的后端进程使用。

```bash
cp deploy/stacks/infra/.env.example deploy/stacks/infra/.env
./deploy/deployment.sh up --stack infra
./deploy/deployment.sh render-backend-env --stack infra
```

默认 Compose project 为 `community-infra`，数据卷前缀为 `community_infra`。所有宿主机依赖端口只绑定
`127.0.0.1`，并与完整 `single` Stack 使用不同的网络、数据卷和端口。
