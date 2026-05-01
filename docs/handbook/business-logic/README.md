# 业务实现逻辑文档

本目录用于沉淀“业务功能在当前代码里是如何落地实现的”。

和 `docs/handbook/ARCHITECTURE.md`、`docs/handbook/SYSTEM_DESIGN.md` 这类偏架构/设计文档不同，这里的文档更关注：

- 某个业务能力的入口在哪里
- 核心链路经过哪些模块、服务、topic、表或状态
- 正常路径、失败路径、补偿路径分别如何工作
- 代码中的关键类、接口和职责边界是什么

约定：

- 一篇文档只解释一个明确业务能力或一条关键业务链路
- 以当前仓库代码为准，避免写成脱离实现的“理想设计”
- 优先给出时序图、关键步骤和代码定位，方便排查与后续维护

当前文档：

- `admin-user-role-management-flow.md`：管理员按 userId / 用户名 / 邮箱定位用户，以及角色修改、二次确认、自降权保护与审计日志链路说明
- `analytics-uv-dau-flow.md`：UV / DAU 的记录、Redis HyperLogLog / Bitmap 存储、区间合并与查询限制链路说明
- `analytics-ingest-flow.md`：analytics 当前只有查询面，`recordUv/recordDau` 在生产代码里尚无真实埋点接入点的现状说明
- `auth-registration-login-flow.md`：认证注册、邮箱验证码验证、密码登录与会话续期链路说明
- `content-post-comment-bookmark-subscription-flow.md`：帖子、评论、收藏、分类订阅与内容写入后下游投影链路说明
- `growth-task-grant-level-flow.md`：帖子 / 评论 / 被点赞 / 签到如何推进任务进度，奖励如何统一发放到钱包，以及用户等级如何按签到任务计算说明
- `growth-checkin-task-center-flow.md`：growth 域旧签到 / 任务中心表面已退休、当前仅保留任务投影与等级计算底座的现状说明
- `im-private-message-flow.md`：IM 私信链路实现说明
- `im-room-message-flow.md`：IM 群聊链路实现说明
- `market-order-dispute-flow.md`：上架、库存、下单、托管、交付 / 发货、确认、取消、争议、管理员裁决与自动确认链路说明
- `notice-projection-read-flow.md`：评论、点赞、关注、治理事件如何投影成站内通知，以及通知列表 / 未读 / 摘要 / 已读链路说明
- `ops-scheduler-compensation-flow.md`：本地定时任务、XXL-Job、outbox worker、single-flight、重试队列如何承担清理、追平、自动动作与补偿说明
- `report-moderation-flow.md`：举报创建、治理动作执行、内容下线、用户处罚与治理通知链路说明
- `search-projection-reindex-flow.md`：帖子事件如何投影到搜索索引，以及搜索查询、reindex、单飞控制与运维触发链路说明
- `security-authz-boundary-flow.md`：`community-app` 当前两条 security filter chain、JWT authorities 解析、`ApiSecurityRules` 授权矩阵与默认 authenticated 兜底说明
- `shared-outbox-delivery-guarantee-flow.md`：共享 outbox 的 BEFORE_COMMIT 入库、worker 轮询、lease 恢复、重试、dead 以及业务 handler 幂等边界说明
- `social-block-im-governance-flow.md`：拉黑关系如何影响评论互动与 IM 私信治理链路说明
- `social-like-follow-outbox-flow.md`：点赞、关注与社交事件 outbox 链路说明
- `startup-fail-closed-runtime-flow.md`：prod 启动校验、AuthStartupValidator、Prometheus basic auth、XXL-Job、scheduler、outbox 等运行时基础设施的 fail-closed 与条件装配说明
- `user-moderation-state-flow.md`：禁言 / 封禁状态如何写入 user 域，并反向影响发帖、评论、私信等写路径说明
- `user-profile-avatar-flow.md`：用户资料聚合现状、头像上传 token / 上传 / 确认，以及 `/files/**` 文件访问链路说明
- `wallet-ledger-flow.md`：钱包摘要、充值、提现、转账、双分录总账、冻结、冲正，以及奖励 / 市场接入钱包链路说明
