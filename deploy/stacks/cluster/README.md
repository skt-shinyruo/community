# Cluster Stack

完整的多节点 Docker Stack，用于多副本、服务发现和集群路径验证。

```bash
cp deploy/stacks/cluster/.env.example deploy/stacks/cluster/.env
./deploy/deployment.sh up --stack cluster
```

默认 Compose project 为 `community-cluster`，数据卷前缀为 `community_cluster`。
