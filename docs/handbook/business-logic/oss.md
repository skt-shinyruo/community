# OSS 对象存储业务逻辑

`community-oss` 是独立 deployable，拥有所有文件类对象的技术事实：对象元数据、版本、alias、访问授权、引用关系、生命周期和底层 blob 位置。业务域只保存自己的业务事实或展示投影，不直接访问 OSS 表、Garage、R2、Ceph RGW 或任何对象存储凭证。

## Owner / SSOT

- `community-oss` owns `community_oss` schema 中的对象、版本、上传会话、alias、usage policy、grant 和 reference。
- Garage / Ceph RGW / local filesystem 只保存 blob；它们不是权限、版本、生命周期或引用关系的 owner。
- 消费方 owner 仍决定业务动作是否允许发生，例如 user 域决定用户是否可以换头像，OSS 决定对象上传、读取和签名 URL 是否符合技术策略。

## Entry

HTTP：

- `POST /api/oss/objects/upload-sessions`
- `POST /api/oss/objects/{objectId}/complete`
- `GET /api/oss/objects/{objectId}`
- `GET /api/oss/objects/{objectId}/signed-url`
- `GET /files/**`

Gateway：

- `/api/oss/**` 路由到 `community-oss`。
- `/files/**` 路由到 `community-oss`，旧头像路径通过 `oss_object_alias` 兼容。

## Main Path

上传会话：

1. 消费方先完成自己的业务授权。
2. 消费方通过 `community-oss-client` 请求 OSS prepare upload。
3. OSS 记录对象、版本和上传会话，声明 owner context、visibility、alias 和期望 content metadata。
4. OSS 返回 proxy upload token 或直接上传能力。

代理上传：

1. 消费方接收浏览器 multipart 后，通过 client 调 OSS complete。
2. OSS 写入 `ObjectStore`，再读取 head 确认 blob 存在。
3. OSS 激活 version，更新 object current version。
4. 如果有 alias，写入 active alias。
5. 返回 canonical public URL，例如 `/files/{objectId}/{versionId}/{fileName}`。

下载：

- `PUBLIC` 对象可匿名走 `/files/**`。
- UUID 形式的 `/files/{objectId}/{versionId}/{fileName}` 以 `objectId + versionId` 为 authority。
- 旧路径如 `/files/avatar/{userId}/{uuid}` 通过 alias 解析到 canonical version。

## Storage Backends

`community-oss` 只依赖 `ObjectStore` port：

- local filesystem：dev / tests。
- S3-compatible：Garage 首版生产后端，也可替换为 Ceph RGW。

单机开发可以使用 local filesystem 或 Garage single-node。生产拓扑至少 3 节点 Garage，开启副本、健康检查、日志和 Prometheus 监控。以后换 Ceph RGW 时，只替换 `ObjectStore` adapter 和配置，`/api/oss/**`、`/files/**` 与 consumer client contract 不变。

## Current Consumer

当前 live consumer 是 user avatar：

1. `UserAvatarApplicationService` 做本人权限检查。
2. `OssAvatarStorageAdapter` 通过 `community-oss-client` prepare / complete。
3. user 仍保存 `headerUrl` 展示投影。
4. canonical 对象事实在 OSS，旧 `avatar/{userId}/{uuid}` 作为 alias 保持可读。

## Key Code

- `backend/community-oss/src/main/java/com/nowcoder/community/oss/OssApplication.java`
- `oss.controller.OssObjectController`
- `oss.controller.PublicFileController`
- `oss.application.ObjectUploadApplicationService`
- `oss.application.ObjectQueryApplicationService`
- `oss.application.ObjectAccessApplicationService`
- `oss.domain.model.*`
- `oss.infrastructure.storage.ObjectStore`
- `oss.infrastructure.storage.LocalFilesystemObjectStore`
- `oss.infrastructure.storage.S3CompatibleObjectStore`
- `backend/community-oss-client/src/main/java/com/nowcoder/community/oss/client/CommunityOssClient.java`
- `user.infrastructure.oss.OssAvatarStorageAdapter`
