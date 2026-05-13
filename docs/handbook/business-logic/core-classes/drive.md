# Drive 核心类细分

本文是 [../drive.md](../drive.md) 的类级补充。网盘域关注的是空间、条目树、回收站和分享，不是对象存储本身。

## 先读顺序

1. `DriveSpaceApplicationService`
2. `DriveEntryApplicationService`
3. `DriveUploadApplicationService`
4. `DriveTrashApplicationService`
5. `DriveShareApplicationService`

## 应用服务

| 类 | 核心职责 | 读代码时重点看什么 |
| --- | --- | --- |
| `drive.application.DriveSpaceApplicationService` | 空间 lazy create、quota / used / remaining 查询。 | 看默认配额如何初始化。 |
| `drive.application.DriveEntryApplicationService` | 文件夹、列表、搜索、重命名、移动和私有下载 URL。 | 看目录树状态和路径校验。 |
| `drive.application.DriveUploadApplicationService` | 上传会话、OSS prepare/complete、quota reserve 和 entry 创建。 | 看它如何把空间占用和对象上传串起来。 |
| `drive.application.DriveTrashApplicationService` | 回收站、恢复、彻底删除、quota 释放和 OSS 删除重试。 | 看恢复和彻底删除如何保持幂等。 |
| `drive.application.DriveShareApplicationService` | 分享创建、撤销、提取码校验、ticket 和分享下载 URL。 | 看分享状态、密码和过期时间如何耦合。 |

## 领域服务和端口

| 类 | 核心职责 |
| --- | --- |
| `drive.domain.service.DriveEntryDomainService` | 文件名规范化、禁止移动到自身 / 子孙目录。 |
| `drive.application.port.DriveShareTicketCodec` | share download ticket 编解码端口。 |
| `drive.infrastructure.security.HmacDriveShareTicketCodec` | HMAC ticket 的具体实现。 |

## 关键基础设施

| 类 | 核心职责 |
| --- | --- |
| `drive.infrastructure.oss.OssDriveObjectStorageAdapter` | drive 到 OSS 的对象存储适配。 |
| `drive.infrastructure.persistence.*` | space、entry、upload、share、share access 的持久化实现。 |

## 关键语义

- `drive` 拥有“用户网盘里有什么”，`OSS` 拥有“文件对象和版本在哪里”。
- 条目状态围绕 `ACTIVE/TRASHED/DELETED` 转换。
- `share verify` 失败不回滚访问记录。
- 彻底删除先收敛数据库和 quota，再做 OSS blob 清理。

