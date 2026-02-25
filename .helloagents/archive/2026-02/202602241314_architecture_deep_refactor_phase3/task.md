# 任务清单（architecture deep refactor - phase3）

- [√] 新增 social block 扫描 RPC：`SocialBlockScanRpcService` + `SocialBlockScanResponse`
- [√] social-service 实现 scan：BlockMapper keyset 扫描 + DubboService 实现
- [√] message-service：新增 block 投影 bootstrap job（scan -> upsert）
- [√] content-service：新增 block 投影 bootstrap job（scan -> upsert）
- [√] 收敛投影语义：`checkEitherBlocked` 空结果 => `NOT_BLOCKED`（移除 UNKNOWN 分支）
- [√] 写路径移除同步回源：私信发送/评论写入不再调用 `isEitherBlocked`
- [√] 清理不再使用的 social block client（如无引用）
- [√] 更新/新增单测与架构门禁（防回潮）
- [√] 同步知识库：`.helloagents/*` + `.helloagents/CHANGELOG.md`
- [√] 迁移方案包到 `.helloagents/archive/2026-02/` 并更新 `.helloagents/archive/_index.md`
