# OSS 对象存储业务逻辑

`community-oss` 是独立 deployable，拥有所有文件类对象的技术事实：对象元数据、版本、访问授权、引用关系、生命周期和底层 blob 位置。业务域只保存自己的业务事实或展示投影，不直接访问 OSS 表、Garage、Ceph RGW、local filesystem 或任何对象存储凭证。

## Owner / SSOT

- `community-oss` owns `community_oss` schema 中的对象、版本、上传会话、usage policy、grant 和 reference。
- Garage / Ceph RGW / local filesystem 只保存 blob；它们不是权限、版本、生命周期或引用关系的 owner。
- 消费方 owner 仍决定业务动作是否允许发生，例如 user 域决定用户是否可以换头像，OSS 决定对象上传、读取和签名 URL 是否符合技术策略。

## Entry

HTTP：

- `POST /api/oss/objects/upload-sessions`
- `POST /api/oss/objects/{objectId}/complete`
- `GET /api/oss/objects/{objectId}`
- `GET /api/oss/objects/{objectId}/signed-url`
- `POST /api/oss/objects/{objectId}/grants`
- `DELETE /api/oss/objects/{objectId}/grants/{grantId}`
- `DELETE /api/oss/objects/{objectId}`
- `POST /internal/oss/objects/{objectId}/references`
- `DELETE /internal/oss/objects/{objectId}/references/{referenceId}`
- `GET /files/**`

Gateway：

- `/api/oss/**` 路由到 `community-oss`。
- `/files/**` 路由到 `community-oss`，只解析 canonical 对象 URL。

## 数据流

OSS 的数据流只负责对象技术事实，业务授权仍由消费方 owner 决定：

1. 上传：消费方先完成自己的业务授权，再通过 `community-oss-client` 请求 prepare upload。OSS 保存对象、版本、上传会话和 owner context，返回通用上传能力而不是存储凭证。
2. 完成上传：消费方把浏览器上传结果回传给 OSS complete。OSS 检查 blob 是否存在，激活 version，并更新 object current version。
3. 下载：`PUBLIC` 对象可匿名走 `/files/**`。canonical URL 以 objectId + versionId 为 authority。
4. 引用和授权：业务 owner 在自己的主事实写入路径内先判断是否允许，再通过 OSS reference / grant API 绑定引用或发放临时访问权。OSS 只记录技术授权事实。
5. 删除：对象删除先看 active reference 和 grant；如果还存在 active 依赖，只能进入 delete pending。没有 active 依赖时才删除 blob、purge version，并把对象标记为 purged。

## 详细链路

上传会话：

1. 消费方先完成自己的业务授权。
2. 消费方通过 `community-oss-client` 请求 OSS prepare upload。
3. OSS 记录对象、版本和上传会话，声明 owner context、visibility 和期望 content metadata。
4. OSS 返回上传会话能力；面向浏览器时，owner domain 只暴露通用上传指令，不暴露 storage provider、bucket 或对象存储凭证。

代理上传：

1. 消费方接收浏览器 multipart 后，通过 client 调 OSS complete。
2. OSS 写入 `ObjectStore`，再读取 head 确认 blob 存在。
3. OSS 激活 version，更新 object current version。
4. 返回 canonical public URL，例如 `/files/{objectId}/{versionId}/{fileName}`。

下载：

- `PUBLIC` 对象可匿名走 `/files/**`。
- `/files/{objectId}/{versionId}/{fileName}` 以 `objectId + versionId` 为 authority。

引用关系：

1. consumer owner 在自己的业务授权和主事实写入路径内决定是否需要绑定 OSS reference。
2. `ObjectReferenceApplicationService.bindReference(...)` 校验 object 存在。
3. 如果命令没有指定 versionId，则默认绑定 object 的 current version。
4. 指定 versionId 时必须属于该 object，并且版本状态必须是 `ACTIVE`。
5. reference 记录 subject service/domain/type/id、referenceRole、创建时间和可选 retainUntil。
6. `releaseReference(...)` 校验 reference 属于该 object 后把 reference 标记 released。

访问授权：

1. `ObjectPermissionApplicationService.grantAccess(...)` 校验 object、principalType、principalValue 和 permission。
2. 未指定 versionId 时默认授权 current version；指定 versionId 时必须属于该 object 且版本为 `ACTIVE`。
3. grant 记录 principal、permission、expiresAt、actor 和创建时间。
4. `revokeAccess(...)` 校验 grant 属于 object 后标记 revoked。
5. grant / revoke 是 OSS 技术授权事实；业务是否允许授权仍由 consumer owner 先判断。

生命周期删除：

1. `ObjectLifecycleApplicationService.deleteObject(...)` 校验 object 存在。
2. 已 `PURGED` 的 object 直接返回 already purged。
3. 如果 object 仍有 active reference 或 active grant，只把 object 标记为 `DELETE_PENDING`。
4. 没有 active reference/grant 时，删除 current version 对应的 ObjectStore blob。
5. blob 删除后把 version 标记 purged，再把 object 标记 `PURGED`。
6. 目前删除只处理 current version；旧版本生命周期需要以后按版本清理能力扩展。

## Storage Backends

`community-oss` 只依赖 `ObjectStore` port：

- local filesystem：dev / tests。
- S3-compatible：Garage 首版生产后端，也可替换为 Ceph RGW。

单机开发可以使用 local filesystem 或 Garage single-node。生产拓扑至少 3 节点 Garage，开启副本、健康检查、日志和 Prometheus 监控。以后换 Ceph RGW 时，只替换 `ObjectStore` adapter 和配置，`/api/oss/**`、`/files/**` 与 consumer client contract 不变。

## Current Consumers

当前 live consumers 包括：

1. `user` avatar：`UserAvatarApplicationService` 做本人权限检查，`OssAvatarStorageAdapter` 通过 `community-oss-client` prepare / complete，user 仍保存 `headerUrl` 展示投影，canonical 对象事实在 OSS。
2. `content` post media：帖子媒体业务引用由 content 负责，文件对象、版本和 public file URL 由 OSS 负责。
3. `drive` cloud drive：目录、配额、回收站和分享由 drive 负责，文件对象、版本、签名下载和生命周期由 OSS 负责。

## Key Code

- `backend/community-oss/src/main/java/com/nowcoder/community/oss/OssApplication.java`
- `oss.controller.OssObjectController`
- `oss.controller.PublicFileController`
- `oss.application.ObjectUploadApplicationService`
- `oss.application.ObjectQueryApplicationService`
- `oss.application.ObjectAccessApplicationService`
- `oss.application.ObjectReferenceApplicationService`
- `oss.application.ObjectPermissionApplicationService`
- `oss.application.ObjectLifecycleApplicationService`
- `oss.domain.model.*`
- `oss.infrastructure.storage.ObjectStore`
- `oss.infrastructure.storage.LocalFilesystemObjectStore`
- `oss.infrastructure.storage.S3CompatibleObjectStore`
- `backend/community-oss-client/src/main/java/com/nowcoder/community/oss/client/CommunityOssClient.java`
- `user.infrastructure.oss.OssAvatarStorageAdapter`
