# mock-data-studio

仅用于本地开发的同步 CLI。它使用可复现的生成器直接写入 `community` / `im_core`，将实体引用记录到批次中，并支持按依赖顺序删除整个批次。

## 使用

先启动 `single` 或 `cluster`，再通过受支持的部署入口运行：

```bash
./deploy/deployment.sh mock-data --stack single -- generate --seed demo
./deploy/deployment.sh mock-data --stack single -- generate \
  --scene im-busy --seed demo --users 20 --posts 40 --comments 80
./deploy/deployment.sh mock-data --stack single -- delete <batch-id>
```

命令同步完成并向标准输出打印 JSON。`generate` 输出的 `batchId` 直接用于 `delete`；没有 HTTP 服务、UI、任务轮询或作业历史。
若生成进程被强制终止并留下 `running` 批次，确认该进程已退出后使用 `delete --force <batch-id>`。

可用 scene：

- `tech-community-hot-start`
- `moderation-pressure`
- `im-busy`

`--users`、`--posts`、`--comments` 覆盖 scene 的默认规模。`--seed` 固定生成内容；省略时使用批次 ID。

也可以连接宿主机可达的 MySQL 直接运行：

```bash
MOCK_DATA_STUDIO_DB_URL='mysql://127.0.0.1:3306/community' \
MOCK_DATA_STUDIO_DB_USER='mock_data_studio' \
MOCK_DATA_STUDIO_DB_PASSWORD='mockdatastudiopass' \
npm --prefix tools/mock-data-studio start -- generate --seed demo
```

关键环境变量：

- `MOCK_DATA_STUDIO_DB_URL`、`MOCK_DATA_STUDIO_DB_USER`、`MOCK_DATA_STUDIO_DB_PASSWORD`：必填数据库连接。
- `MOCK_DATA_AUTO_FILL_SCENE`：默认 `tech-community-hot-start`。
- `MOCK_DATA_DEFAULT_USERS`、`MOCK_DATA_DEFAULT_POSTS`、`MOCK_DATA_DEFAULT_COMMENTS`：默认 `100`、`800`、`2500`。
- `MOCK_DATA_STUDIO_REINDEX_JWT_HMAC_SECRET`：可选；设置后，生成帖子或评论会请求 search reindex。
- `MOCK_DATA_STUDIO_COMMUNITY_APP_BASE_URL`：默认 `http://community-app:8080`。

## 验证

```bash
npm --prefix tools/mock-data-studio test
npm --prefix tools/mock-data-studio start -- --help
```

写入逻辑只服务本地 demo。评论、关注、点赞、治理、growth task progress 和 IM 数据仍遵循当前 schema；删除依赖 `demo_entity_ref`，不要手工删除批次引用后再调用 `delete`。
