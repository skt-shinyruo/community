# 网盘、分享和 OSS 文件流程

本文解释用户网盘如何和 OSS 对象存储协作。领域细节见 [../drive.md](../drive.md) 和 [../oss.md](../oss.md)。

## 参与领域

| 领域 | 职责 |
| --- | --- |
| drive | 空间配额、目录树、文件条目、回收站、分享 token、提取码和访问日志。 |
| OSS | 对象、版本、上传会话、alias、reference、grant、签名 URL 和 blob 生命周期。 |
| user | 用户身份和业务授权。 |

## 上传流程

1. 用户进入网盘时，drive 读取 `drive_space`，不存在则创建默认配额。
2. 用户准备上传文件。
3. `DriveUploadApplicationService.prepareUpload(...)` 校验父目录、文件名、文件大小和剩余空间。
4. drive 通过 OSS prepare upload 创建对象上传会话。
5. 浏览器上传 blob 到 OSS 支持的后端。
6. `completeUpload(...)` 成功后，OSS 激活 object/version。
7. drive 把 OSS object/version 绑定到 `drive_upload`。
8. drive 落成新的 file entry，并更新 used/quota。

drive 拥有“用户网盘里有什么”的业务事实；OSS 拥有“文件对象和版本在哪里”的技术事实。

## 目录和回收站

目录树由 drive 维护：

- create folder 创建目录 entry。
- rename 修改条目名称。
- move 改变父目录关系。
- search 在 drive 条目范围内查找。
- trash 将条目标记为 `TRASHED`。
- restore 按 trashRootId 恢复整棵子树。
- permanent delete 收敛数据库和 quota 后，再调用 OSS 清理 blob。

条目状态围绕 `ACTIVE`、`TRASHED`、`DELETED` 转换。不要直接通过 OSS 删除 blob 来表达网盘删除。

## 分享和下载

1. 用户创建分享链接，drive 生成 share token 和提取码 hash。
2. 访问者提交 token 和提取码。
3. drive 校验分享状态、提取码、过期时间和条目状态。
4. 校验成功后，drive 发放短时访问 ticket。
5. drive 调 OSS 获取签名 download URL 或 grant。
6. 访问日志写入 `drive_share_access`。
7. 实际文件读取由 OSS `/files/**` 或签名 URL 承担。

分享是否可下载由 drive 的条目和 share 状态决定；对象读取能力由 OSS 决定。

## OSS 通用语义

OSS 对象存储服务还被 user 头像、content 帖子媒体和 drive 网盘使用。

- `PUBLIC` 对象可以匿名走 `/files/**`。
- canonical URL 以 objectId + versionId 为 authority。
- 旧路径可以通过 alias 解析到 canonical version。
- 删除对象时要看 active reference 和 grant。
- 有 active 依赖时只能进入 delete pending，不能直接 purge blob。

## 排查口径

| 现象 | 先查哪里 |
| --- | --- |
| 网盘显示没文件 | drive entry 和 space，不要先查 OSS blob。 |
| 上传完成但不可下载 | OSS object/version、drive upload 绑定、grant 或签名 URL。 |
| 分享提取码失败 | drive share token、提取码 hash、过期时间和 access ticket。 |
| 删除后空间没释放 | drive quota 收敛和 OSS reference 清理。 |
