## 方案概述（方案 3：深度重构）

本阶段采用“投影自举 + 事件持续纠偏”的方式，彻底移除写路径同步依赖：

1. 在 `social-service` 新增 **block 扫描 RPC**（internal Dubbo）：
   - `SocialBlockScanRpcService#scan(afterUserId, afterTargetUserId, limit)` 以 keyset 分页方式扫描当前 `social_block` 表中的拉黑关系集合。
   - 返回 next cursor + hasMore，供下游服务循环拉取。

2. 在 `message-service` 与 `content-service` 新增 **block 投影 bootstrap job**：
   - 周期性调用 scan RPC，批量 upsert `user_block_projection`（只需要写入 blocked=1 的方向记录）。
   - 与 Kafka 事件消费者并行：事件流对 block/unblock 做实时纠偏，scan 用于冷启动与补洞。

3. 收敛 `checkEitherBlocked` 语义：
   - 当本地投影查询不到记录时，默认返回 `NOT_BLOCKED`（不再引入 `UNKNOWN`）。
   - 写路径只依赖本地投影判断（若事件/scan 后续发现 blocked，将在下一次操作时体现）。

4. 移除写路径同步回源与相关 client：
   - `message-service.PrivateMessageService#send` 删除 `SocialServiceClient.isEitherBlocked` 回源分支。
   - `content-service.CommentService#addComment` 删除 `SocialBlockClient.isEitherBlocked` 回源分支。
   - 清理不再使用的 `SocialServiceClient` / `SocialBlockClient`（若确认无其他引用）。

5. 门禁与回归：
   - 更新/新增单元测试，确保写路径不再触发下游 RPC。
   - 添加架构门禁（ArchUnit/源码扫描类测试）防止写路径重新引入同步回源。

## 风险与缓解

- 风险：在 bootstrap job 尚未完整扫描前，可能存在“历史已拉黑但投影尚未覆盖”的窗口期。
  - 缓解：提高 job 的启动频率/批次大小，优先完成全量扫描；并保留事件消费者的实时纠偏。
  - 说明：该窗口期属于“最终一致系统的冷启动收敛成本”，相比写路径强依赖 SSOT，更符合整体架构目标。

