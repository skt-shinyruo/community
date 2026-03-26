# mock-data-studio

本目录提供一个仅用于本地开发 / 演示环境的 `mock-data-studio` 服务骨架。

当前阶段包含：
- `GET /` 最小操作台 UI（生成 / 运行态 / 历史 / 详情）
- `GET /health` 健康检查
- `GET /api/runtime-status` 运行态 readiness（DB / community-app / im-core / AI env）
- `POST /api/jobs` 创建单飞行 demo job
- `GET /api/jobs/:jobId` 查询 job 状态
- `GET /api/batches` 查询默认批次 + 手动批次历史
- `GET /api/batches/:batchId` 查询批次详情（target / actual / failure summaries）
- `DELETE /api/batches/:batchId` 按依赖顺序删除批次写入结果与 metadata（运行中 job 会阻止删除）
- startup auto-fill 复用稳定默认批次；手动 `POST /api/jobs` 使用当前 job 自己创建的批次承载 targets / refs / 写入结果
- startup auto-fill deficit planning（默认批次目标/缺口计算）
- `write-community`：直接写入 Phase 1 + Phase 2 社区样例：
  - `user` / `discuss_post` / `comment` / `social_follow` / `social_like`
  - `message`（私信 + notice）
  - `report` / `moderation_action`
  - `growth_check_in` / `user_task_progress`
  - `reward_account` / `reward_ledger` / `reward_grant_record`
  - `reward_item` / `reward_order`
- `write-im`：直接写入 `im_core.im_room` / `im_room_member` / `im_room_message` / `im_conversation` / `im_private_message`
- 可复现的 seedable 内容生成（用户、帖子、两层评论树、关注/点赞关系）
- 生成结果写入 `demo_entity_ref`，为后续 batch delete / reindex hook 提供实体引用
- content-like 生成结果触发 search reindex completion hook
- 环境变量解析与启动日志
- `docker compose` 接线（端口仅绑定到宿主机 `127.0.0.1`）

## 本地运行

安装依赖：

```bash
npm --prefix tools/mock-data-studio install
```

运行测试：

```bash
npm --prefix tools/mock-data-studio test -- \
  test/env.test.mjs \
  test/health.test.mjs \
  test/runtime-status.test.mjs \
  test/job-runner.test.mjs \
  test/ui-api-contract.test.mjs \
  test/planner.test.mjs \
  test/delete-batch-service.test.mjs
```

直接启动：

```bash
MOCK_DATA_STUDIO_DB_URL='mysql://127.0.0.1:3306/community' \
MOCK_DATA_STUDIO_DB_USER='mock_data_studio' \
MOCK_DATA_STUDIO_DB_PASSWORD='mockdatastudiopass' \
npm --prefix tools/mock-data-studio start
```

默认 bind host 为 `127.0.0.1`，默认进程监听端口为 `12888`。

`docker compose` 路径下：
- `MOCK_DATA_STUDIO_PORT` 表示容器内 studio 进程的监听端口
- `MOCK_DATA_STUDIO_HOST_PORT` 表示宿主机 `127.0.0.1` 上暴露的端口

## 集成说明

当前阶段 **compose 是 upstream 集成的主支持路径**。原因是：
- compose 内部已经提供 `mysql`、`community-app`、`im-core` 的服务发现地址
- 直接在宿主机运行时，仓库默认不会把 MySQL 暴露到宿主机

如果你只是调试这个 Node 壳层本身，直接宿主机运行没问题；如果你要让 studio 真正连到 upstream，请自行提供可达的 MySQL，并显式覆盖：

```bash
MOCK_DATA_STUDIO_DB_URL='mysql://127.0.0.1:3306/community' \
MOCK_DATA_STUDIO_DB_USER='mock_data_studio' \
MOCK_DATA_STUDIO_DB_PASSWORD='mockdatastudiopass' \
MOCK_DATA_STUDIO_COMMUNITY_APP_BASE_URL='http://127.0.0.1:12882' \
MOCK_DATA_STUDIO_IM_CORE_BASE_URL='http://127.0.0.1:18082' \
npm --prefix tools/mock-data-studio start
```

上面的 `community-app` / `im-core` 直连地址依赖 `debug` profile；MySQL 需要你自行提供宿主机可达的实例或额外端口映射。

当前 API 示例：

```bash
curl http://127.0.0.1:12888/
curl http://127.0.0.1:12888/health
curl http://127.0.0.1:12888/api/runtime-status
curl -X POST http://127.0.0.1:12888/api/jobs -H 'content-type: application/json' \
  -d '{"requestedBy":"local-dev","batchType":"demo-seed","jobType":"demo-seed"}'
curl http://127.0.0.1:12888/api/jobs/1
curl http://127.0.0.1:12888/api/batches
curl http://127.0.0.1:12888/api/batches/1
curl -X DELETE http://127.0.0.1:12888/api/batches/1
```

## 最小 UI 说明

- `/` 提供一个无需前端框架的单页壳层，覆盖：
  - 运行态卡片
  - 生成表单预览（模式 / 预设 / 计数 / AI toggle）
  - job 轮询结果
  - batch 历史表格
  - batch 详情面板
- `/health` 额外返回 `ui.generateForm` 元数据，给静态页面初始化模式、预设与默认预览值。
- `/api/runtime-status` 额外返回 `summary` 与 `cards`，供状态卡片直接渲染。
- `/api/jobs` 与 `/api/jobs/:jobId` 额外返回 `request` / `polling` 元数据，便于前端轮询与跳转 batch 详情。
- `/api/batches` 与 `/api/batches/:batchId` 额外返回 `history` / `detail` 元数据，便于表格与详情面板渲染。

## 关键环境变量

- `MOCK_DATA_STUDIO_PORT`：服务监听端口，默认 `12888`
- `MOCK_DATA_STUDIO_HOST_PORT`：仅 compose 使用的宿主机映射端口，默认 `12888`
- `MOCK_DATA_STUDIO_BIND_HOST`：服务绑定地址，默认 `127.0.0.1`；compose 内会覆盖为 `0.0.0.0`
- `MOCK_DATA_STUDIO_DB_URL`：必填，studio 访问 MySQL 的连接串
- `MOCK_DATA_STUDIO_DB_USER`：必填，studio 使用的专用数据库账号；compose 默认值为 `mock_data_studio`，对 `community` schema 具有 `select/insert/update/delete/create`，对 `im_core` schema 具有 `select/insert/update/delete`
- `MOCK_DATA_STUDIO_DB_PASSWORD`：必填，studio 使用的数据库口令；compose 默认值为 `mockdatastudiopass`
- `MOCK_DATA_STUDIO_COMMUNITY_APP_BASE_URL`：默认 `http://community-app:8080`
- `MOCK_DATA_STUDIO_IM_CORE_BASE_URL`：默认 `http://im-core:18082`
- `MOCK_DATA_STUDIO_ENABLED`：默认 `true`；设为 `false` 时服务进程直接退出
- `MOCK_DATA_AUTO_FILL_ENABLED`：默认 `false`；设为 `true` 时服务启动后自动提交一次 startup deficit-fill job
- `MOCK_DATA_AUTO_FILL_SCENE`：默认 `tech-community-hot-start`
- `MOCK_DATA_DEFAULT_USERS`：默认 `100`
- `MOCK_DATA_DEFAULT_POSTS`：默认 `800`
- `MOCK_DATA_DEFAULT_COMMENTS`：默认 `2500`
- `MOCK_DATA_STUDIO_AI_ENABLED`：默认 `false`；仅在手动 `manual-generate` 且勾选 AI 增强时生效
- `MOCK_DATA_STUDIO_OPENAI_API_KEY`：OpenAI key；为空时回退 `OPENAI_API_KEY`
- `MOCK_DATA_STUDIO_OPENAI_MODEL`：默认 `gpt-4.1-mini`
- `MOCK_DATA_STUDIO_OPENAI_TIMEOUT_MS`：默认 `8000`
- `MOCK_DATA_STUDIO_AI_MAX_ITEMS_PER_JOB`：默认 `20`；超出预算的文本保持规则生成结果
- `MOCK_DATA_STUDIO_REINDEX_JWT_HMAC_SECRET`：可选；为空时回退 `JWT_HMAC_SECRET`
- `MOCK_DATA_STUDIO_REINDEX_JWT_ISSUER`：默认 `community-auth`
- `MOCK_DATA_STUDIO_REINDEX_JWT_TTL_SECONDS`：默认 `120`

## 搜索重建说明

- content-like 结果会在作业结束时调用 `POST /api/ops/search/reindex`
- `community-app` 的 `/api/ops/**` 要求 `ROLE_ADMIN`
- `mock-data-studio` 会在本地 dev 中生成一个短时 `ROLE_ADMIN` JWT 来触发该操作
- 默认优先使用 `MOCK_DATA_STUDIO_REINDEX_JWT_HMAC_SECRET`，未设置时回退 `JWT_HMAC_SECRET`

## 写入说明

- `write-community` 会先补齐社区 Phase 1，再根据当前 batch plan 追加社区 / moderation / growth / reward Phase 2 样例。
- 评论语义与线上契约保持一致：
  - 直接评论：`comment.entity_type = 1`，`comment.entity_id = postId`
  - 回复评论：`comment.entity_type = 2`，`comment.entity_id = parentCommentId`
- 关注仅写 `USER` 目标：`social_follow.entity_type = 3`
- 点赞仅写帖子/评论：`social_like.entity_type in (1, 2)`
- `message` 表会拆成两个 demo entity type：
  - `messages`：普通私信（`conversation_id = minUserId_maxUserId`）
  - `notices`：系统通知（`from_id = 0`，`conversation_id in comment/like/follow/moderation`）
- Phase 2 IM 写入不额外生成 read-state；当前 UI / API 在 read-state 缺失时会按 `0` 处理，因此仍能展示非空房间、会话和未读摘要。
- 可见聚合字段会同步维护：
  - `discuss_post.comment_count`
  - `user.score`
- 所有业务写入都会追加 `demo_entity_ref`，因此批次详情 target / actual / failure summary 与 delete 流程都能覆盖 Phase 2 实体。
- AI 文本增强只会修改文本字段（帖子/评论/通知/举报说明/IM 文案等）；数量控制、关系生成、批次管理和写库逻辑仍由规则链路负责。
- 即便手动 job 开启 AI 增强，若 AI 凭据缺失、超时或 provider 异常，也会自动回退规则文案，不会导致整 job 失败。
