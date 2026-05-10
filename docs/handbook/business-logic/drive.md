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

1. 用户进入 `/drive`。
2. 前端先拉取 `space` 投影和当前目录条目。
3. 新建文件夹和上传先进入 drive application service，再通过 drive-owned OSS port 申请上传会话。
4. 上传完成后，drive 生成文件条目并更新配额。
5. 访问者只有拿到分享 token、提取码校验通过并换到短时 ticket 后，才可以向 drive 申请最终下载 URL。
6. 软删除进入回收站，恢复动作回到当前目录或目标目录，彻底删除先收敛数据库和配额，再清理 OSS blob。

## Failure

- 配额不足：直接拒绝上传。
- 文件名冲突：由 drive 侧同目录唯一性规则拒绝。
- 分享密码错误、分享过期、分享撤销：只返回统一失败，不暴露内部对象细节。
- 上传会话过期或重复完成：返回幂等或失效语义。
- OSS 不可用：上传、下载链接签发和生命周期动作会返回网盘存储不可用。

## Key Code

- `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/DriveController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/controller/DrivePublicShareController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/application/*ApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/domain/*`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/infrastructure/*`
- `frontend/src/views/DriveView.vue`
- `frontend/src/views/DriveShareView.vue`
