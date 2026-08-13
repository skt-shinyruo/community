# Single Stack

完整的单节点 Docker Stack，包含基础设施、后端、前端入口和开发工具。

```bash
cp deploy/stacks/single/.env.example deploy/stacks/single/.env
./deploy/deployment.sh up --stack single
```

默认 Compose project 为 `community-single`，数据卷前缀为 `community_single`。
