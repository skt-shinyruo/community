## 背景与问题

当前 `message-service` 与 `content-service` 在写路径（私信发送、评论/回复写入）中，为了做“双方任意一方拉黑则禁止互动”的反骚扰校验，会在本地投影 `user_block_projection` 判定为 `UNKNOWN` 时同步回源 `social-service`（Dubbo RPC：`SocialBlockRpcService#isEitherBlocked`），并将结果回填投影。

该设计在功能上可实现 **fail-closed**（避免因投影缺失而误放行），但带来明显的架构问题：

- 写路径引入跨服务同步依赖：`message-service`/`content-service` 的可用性被 `social-service` 放大（超时/抖动会直接导致写入失败）。
- “冷启动/首次互动”成为常态回源：由于 `social-service` 仅对“拉黑/解除拉黑”产生事件，默认“未拉黑”没有事件，因此大多数用户对第一次互动会落入 `UNKNOWN` 分支并触发同步 RPC。
- 业务边界漂移：反骚扰校验由“本地读模型（最终一致）”退化为“强依赖 SSOT”，与 outbox-only、投影一致性加固方向冲突。

## 目标

在保持“拉黑关系禁止互动”业务语义不变的前提下，完成以下重构目标：

1. **移除写路径中的同步回源**：私信发送/评论写入不再直接 RPC 依赖 `social-service`。
2. **投影可自举（bootstrap）**：通过可扫描的 internal RPC 能力，让下游服务可周期性拉取“当前拉黑关系集合”，在冷启动时构建必要的投影基础。
3. **将缺失语义从‘UNKNOWN’收敛为‘NOT_BLOCKED’**：在完成 blocked 集合的 bootstrap 后，“投影无记录”可被解释为“未拉黑”（由事件流持续纠偏），避免首次互动必回源。
4. **建立门禁防回潮**：用测试/架构约束，禁止写路径再次引入 `SocialBlockRpcService#isEitherBlocked` 同步调用。

