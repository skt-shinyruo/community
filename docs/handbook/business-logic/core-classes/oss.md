# OSS 核心类细分

本文是 [../oss.md](../oss.md) 的类级补充。OSS 的重点不是业务页面，而是对象、版本、授权、引用和生命周期这几个事实。

## 先读顺序

1. `ObjectUploadApplicationService`
2. `ObjectUploadRecoveryApplicationService`
3. `ObjectReferenceApplicationService`
4. `ObjectPermissionApplicationService`
5. `ObjectLifecycleApplicationService`
6. `ObjectQueryApplicationService` / `ObjectAccessApplicationService`

## 应用服务

| 类 | 核心职责 | 读代码时重点看什么 |
| --- | --- | --- |
| `oss.application.ObjectUploadApplicationService` | upload session、complete、metadata 三段式上传。 | 看它如何把对象元数据和版本事实分开。 |
| `oss.application.ObjectUploadRecoveryApplicationService` | stale `UPLOADING` session 的 blob 检查、reset 或 finalize。 | 看 attempt key 和 `claimVersion` fencing。 |
| `oss.application.ObjectUploadTransactionOperations` | upload claim、失败观察、reset 和 finalize 的独立事务。 | 看 session ID + claim version 条件更新。 |
| `oss.application.ObjectQueryApplicationService` | 元数据查询和 public file resolve。 | 看它如何提供给上游 domain 的只读对象视图。 |
| `oss.application.ObjectAccessApplicationService` | signed URL 签发。 | 看权限校验和过期时间如何绑定。 |
| `oss.application.ObjectReferenceApplicationService` | object reference bind / release。 | 看引用计数和对象删除之间的关系。 |
| `oss.application.ObjectPermissionApplicationService` | grant / revoke object access。 | 看 principal 和 permission 的组合规则。 |
| `oss.application.ObjectLifecycleApplicationService` | delete pending / purge lifecycle。 | 看它如何把逻辑删除和物理清理分开。 |

## 领域模型

| 类 | 核心职责 |
| --- | --- |
| `community-oss.domain.model.OssUsagePolicy` | 限制文件大小、MIME、TTL、cache 和生命周期策略。 |

## 关键基础设施

| 类 | 核心职责 |
| --- | --- |
| `community-oss.application.*` | OSS deployable 内部的同名应用服务实现。 |
| `community-oss-client.CommunityOssClient` | 业务服务调用 OSS 的 typed contract。 |
| `community-oss-client.HttpCommunityOssClient` | typed contract 的 HTTP 实现。 |
| `community-oss.infrastructure.persistence.typehandler.UuidBinaryTypeHandler` | OSS schema 的 UUID binary 适配。 |
| `community-oss.infrastructure.job.ObjectUploadRecoveryJob` | 按 stale threshold 和 batch 调度 upload recovery。 |

## 关键语义

- OSS 只拥有对象事实，不拥有上游业务授权。
- 上传会话、引用和生命周期是三条不同的事实线。
- 上传会话使用 `READY -> UPLOADING -> COMPLETED` 和单调 `claimVersion`；每次 attempt 写 `.claim-<claimVersion>`，恢复不能完成已失去的 claim。
- 上游域通常只拿到 signed URL 或对象引用，不直接碰 blob 存储。
