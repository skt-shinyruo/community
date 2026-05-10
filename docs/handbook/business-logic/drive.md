# 网盘业务逻辑

`drive` 是用户私有网盘域。它负责每个用户默认 10GiB 的空间配额、目录树、上传会话、下载入口、回收站和分享链接；真正的对象 blob、版本、签名下载和生命周期仍由 `community-oss` 负责。

## Owner / SSOT

- `drive` owns `community.drive` schema 中的空间、条目、上传、分享和分享访问记录。
- `community-oss` owns 文件对象、版本、短时签名下载和底层存储后端。
- `drive` 默认私有，只有生成带密码和有效期的分享链接后才会对外暴露访问入口。

## Entry

HTTP：

- `GET /api/drive/space`
- `GET /api/drive/entries`
- `GET /api/drive/trash`
- `GET /api/drive/search`
- `POST /api/drive/folders`
- `POST /api/drive/uploads`
- `POST /api/drive/uploads/{uploadId}/complete`
- `POST /api/drive/entries/{entryId}/rename`
- `POST /api/drive/entries/{entryId}/move`
- `POST /api/drive/entries/{entryId}/trash`
- `POST /api/drive/trash/{entryId}/restore`
- `DELETE /api/drive/trash/{entryId}`
- `GET /api/drive/entries/{entryId}/download-url`
- `POST /api/drive/entries/{entryId}/shares`
- `DELETE /api/drive/shares/{shareId}`

Public share HTTP：

- `GET /api/drive/shares/{shareToken}`
- `POST /api/drive/shares/{shareToken}/verify`
- `GET /api/drive/shares/{shareToken}/download-url`

Front-end：

- `DriveView.vue`
- `DriveShareView.vue`

## Main Path

空间：

1. 用户进入 `/drive`。
2. 前端先拉取 `GET /api/drive/space` 和根目录条目。
3. `DriveSpaceApplicationService.getSpace(...)` 按 userId 查空间。
4. 如果空间不存在，创建默认空间；当前默认 quota 是 10GiB。
5. 并发首次创建遇到唯一键冲突时，重新读取已有空间。

目录和文件条目：

1. `DriveEntryApplicationService.createFolder(...)` 校验 actor、父目录、名称和同目录重名。
2. 名称由 `DriveEntryDomainService.normalizeName(...)` 规范化：trim、不能为空、不能包含 `/` 或 `\`、不能是 `.` / `..`、最长 255。
3. `listEntries(...)` 只列 active child；根目录用 `parentId=null`。
4. `search(...)` 对空关键词返回空列表；非空关键词最多返回 50 条 active 命中。
5. `rename(...)` 保持父目录不变，先查重再保存。
6. `move(...)` 校验目标父目录 active folder；移动文件夹时禁止移到自己或子孙目录。
7. 私有下载通过 `createDownloadUrl(...)` 只允许 active file，向 OSS port 申请 600 秒签名下载 URL。

上传：

1. `DriveUploadApplicationService.prepareUpload(...)` 校验父目录、文件名、文件大小和剩余空间。
2. drive 通过 `DriveObjectStoragePort.prepareUpload(...)` 向 OSS 创建 `DRIVE_FILE` / `PRIVATE` 对象上传会话。
3. drive 保存 `drive_upload`，owner context 是 `community-app / drive / drive-upload / <uploadId>`。
4. 返回通用上传指令：`POST /api/drive/uploads/{uploadId}/complete`，multipart field 为 `file`，并要求带回 `fileKey`。
5. `completeUpload(...)` 校验上传会话属于当前用户；已完成会话幂等返回已生成的 entry。
6. 过期会话会被标记完成到一个占位 entryId，并返回上传会话不可用；该标记使用新事务保存，避免随异常回滚。
7. 完成时再次校验文件大小、父目录、重名和剩余空间。
8. drive 先 reserve quota，再通过 OSS complete 写 blob 和激活版本。
9. OSS complete 成功后创建 `DriveEntry` file，并把 upload 标记为 completed。

回收站：

1. `trash(...)` 只允许 active entry；对文件夹会同时把所有子孙 active entry 标记为 trashed。
2. trashed entry 保存 `trashRootId`、`trashedAt` 和 `deleteAfter`，当前保留期是 30 天。
3. `restore(...)` 只允许 trashed root entry，恢复到指定 active folder 或根目录。
4. 恢复根目录时，会恢复同一个 `trashRootId` 下的 trashed 子孙，子孙仍回到原 parent。
5. `deletePermanently(...)` 只允许非 active entry。trashed entry 会先把自身和子孙标记为 deleted，并释放文件大小对应的 quota。
6. 数据库和 quota 收敛后，再调用 OSS delete 清理 blob；如果 OSS 删除失败，entry 已是 deleted，后续再次调用可重试 deleted 文件对象清理。
7. 对已 deleted root 再次 delete，会按 root 更新时间选出可重试的 deleted file，并重新调用 OSS delete。

分享：

1. `createShare(...)` 只允许 owner 对 active file 或 folder 创建分享。
2. 分享必须设置非空提取码和未来过期时间。
3. 分享 token 使用 18 字节随机数做 URL-safe base64；提取码只保存 hash。
4. `loadPublicShare(...)` 只返回 active share 的基础信息，不返回 ticket。
5. `verifyShare(...)` 对过期、撤销、密码错误、目标 entry 不可用都记录 `drive_share_access`；密码错误返回提取码错误。
6. 校验成功后记录成功访问，签发 600 秒短时 ticket。
7. `createShareDownloadUrl(...)` 必须带合法 ticket；分享 file 时只能下载该 file，分享 folder 时只能下载其 active file 子孙。
8. 分享下载 URL 由 OSS 签发，TTL 也是 600 秒。

## Failure

- 配额不足：直接拒绝上传。
- 文件名冲突：由 drive 侧同目录唯一性规则拒绝。
- 分享密码错误、分享过期、分享撤销：只返回统一失败，不暴露内部对象细节。
- 上传会话过期或重复完成：返回幂等或失效语义。
- OSS 不可用：上传、下载链接签发和生命周期动作会返回网盘存储不可用。
- 彻底删除时数据库状态和 quota 先收敛；OSS 删除失败后依靠重复 delete 重试 blob 清理。
- share verify 使用 `noRollbackFor=BusinessException`，因此失败访问记录会保留。

## State

- `drive_space`：每个用户一个空间，记录 quota、used 和更新时间。
- `drive_entry`：目录树节点，`type=FOLDER|FILE`，`status=ACTIVE|TRASHED|DELETED`。
- `drive_upload`：上传会话，保存 OSS object/version/session、文件元数据、创建人、过期时间和 completed entry。
- `drive_share`：分享 token、entry、提取码 hash、过期时间、状态和创建人。
- `drive_share_access`：分享校验访问日志，记录 visitor fingerprint 和 success。

## Frontend State

- `frontend/src/api/services/driveService.js` 是前端 API service。
- `frontend/src/views/driveState.js` 负责 quota 展示、entry capability、breadcrumb、分享表单校验和选择收敛。
- `DriveView.vue` 的上传流程先创建 drive upload session，再按服务端返回的 upload instruction 执行 multipart upload。
- `DriveShareView.vue` 不持久保存提取码；校验成功后只使用后端返回的短时 ticket 拉下载 URL。

## Key Code

- `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/DriveController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/DrivePublicShareController.java`
- `drive.application.DriveSpaceApplicationService`
- `drive.application.DriveEntryApplicationService`
- `drive.application.DriveUploadApplicationService`
- `drive.application.DriveTrashApplicationService`
- `drive.application.DriveShareApplicationService`
- `drive.domain.service.DriveEntryDomainService`
- `drive.application.port.DriveObjectStoragePort`
- `drive.infrastructure.oss.OssDriveObjectStorageAdapter`
- `drive.infrastructure.persistence.*`
- `frontend/src/views/DriveView.vue`
- `frontend/src/views/DriveShareView.vue`
- `frontend/src/views/driveState.js`
- `frontend/src/api/services/driveService.js`
