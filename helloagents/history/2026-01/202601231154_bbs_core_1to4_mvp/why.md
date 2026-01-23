# Change Proposal: BBS 核心运营能力（治理 + 内容生命周期 + 收藏订阅 + 成长体系）

## Requirement Background

当前项目已经具备 BBS 的“可用”核心能力：注册/登录、发帖、评论/回复、点赞、关注、私信/通知、搜索、热帖与基础统计等。  
但要把社区从“能跑 demo”推进到“可长期运营”，还需要补齐 4 类关键业务能力：

1. **社区治理闭环**：举报 → 审核 → 处置 → 通知 → 申诉/复核（最小可运营闭环），并具备审计追溯。
2. **内容生命周期**：作者编辑窗口、软删除与状态展示，减少误操作与提升内容质量。
3. **收藏与订阅**：让用户把信息沉淀到“个人空间”，形成稳定回访路径。
4. **成长体系（轻量）**：积分/等级/榜单，驱动生产与互动（同时要防刷）。

本方案按“先 MVP（可运营闭环）→ 再扩展（更复杂策略）”落地，避免一次性大爆炸导致风险失控。

## Product Analysis

### Target Users and Scenarios
- **游客**：浏览帖子、搜索、查看用户主页、了解社区氛围。
- **登录用户**：发帖、评论/回复、收藏、订阅感兴趣的版块/标签、私信交流。
- **版主/管理员**：处理举报、处置违规内容/账号、维护社区秩序并追溯审计。

### Core Pain Points
1. **治理缺口**：只有管理删除/置顶/加精还不够，缺少“举报入口与审核队列”，导致违规内容无法规模化治理。
2. **内容不可控**：缺少编辑窗口与软删除，会造成纠错困难与误删不可恢复的体验落差。
3. **回访路径弱**：没有收藏/订阅，用户很难沉淀自己的内容清单与兴趣圈层，降低留存与深度。
4. **激励不足**：没有积分/等级/榜单，缺少可持续的正反馈机制；同时缺少防刷策略会导致系统被滥用。

### Value Proposition and Success Metrics
- **价值主张**：用“治理闭环 + 生命周期 + 收藏订阅 + 成长体系”的最小组合，建立一个可长期运营的社区底座。
- **验收指标（建议作为落地验收清单）**：
  1. 举报可提交、可审核、可处置、可追溯；处置结果可通知相关用户。
  2. 作者可在约定窗口内编辑帖子/评论，并显示“已编辑”；删除为软删除并可区分处置来源。
  3. 收藏链路可用：详情页收藏/取消收藏 + 我的收藏列表。
  4. 订阅链路可用：订阅分类/标签 + “仅看订阅”筛选（MVP 可只做分类，标签为可选扩展）。
  5. 积分/等级可用：发帖/评论/获赞驱动积分增长；具备最小防刷（每日上限/互刷限制）；榜单可查看。

### Humanistic Care
- 举报与处置信息最小化披露：避免在公开页面泄露举报人身份与敏感细节。
- 对内容屏蔽/处罚提供清晰解释与申诉入口，减少误伤与对立。
- 反骚扰（拉黑）优先保障“停止打扰”，并避免破坏公共讨论的可见性边界。

## Change Content
1. **举报与审核闭环**：支持“帖子/评论/用户”三类举报；提供审核队列与处置动作（驳回/隐藏/删除/警告/禁言/封禁）；记录审计；可选阈值触发临时隐藏。
2. **拉黑/屏蔽**：A 拉黑 B 后，A 侧隐藏 B 内容、禁止互相私信；B 禁止对 A 评论/回复（MVP）；版主/管理员不受拉黑影响。
3. **内容生命周期**：作者编辑窗口（帖子 24h / 评论 15min）；展示“已编辑”；作者删除为软删除；版主删除仍保留原接口与权限。
4. **收藏与订阅**：收藏帖子（私密）+ 收藏列表；订阅分类/标签（MVP 建议先分类）+ 订阅筛选。
5. **成长体系（轻量）**：积分/等级（事件驱动）；最小防刷；榜单（全站 Top）。

## Impact Scope
- **Modules:** `frontend` | `gateway` | `content-service` | `user-service` | `social-service` | `message-service` | `common` | `deploy`
- **APIs（新增/扩展）**：
  - 举报与治理：`/api/reports/**`、`/api/moderation/**`
  - 拉黑：`/api/blocks/**`
  - 内容编辑：`PUT /api/posts/{id}`、`PUT /api/posts/{postId}/comments/{commentId}`（或等价路径）
  - 收藏：`/api/bookmarks/**`（或等价路径）
  - 订阅：`/api/subscriptions/**`（或等价路径）
  - 成长：`/api/leaderboard/**`、用户资料扩展（score/level）
- **Data:** MySQL 表/字段扩展（content/user），Redis（拉黑关系），Kafka 事件扩展（积分与通知）

## Core Scenarios

### Requirement: BBS-MOD-101 Report & Review Loop
**Module:** content-service / message-service / frontend

#### Scenario: BBS-MOD-101-1 Report Post/Comment/User
登录用户可对“帖子/评论/用户”提交举报：
- 可选择原因（枚举）并填写补充说明（可选）。
- 重复举报（同一举报人对同一目标）应可幂等或提示已举报。
- 举报记录默认对外不可见（仅治理后台可见）。

#### Scenario: BBS-MOD-101-2 Moderator Review & Actions
版主/管理员可在治理后台查看举报队列并处理：
- 处置动作：驳回 / 隐藏内容 / 删除内容 / 警告用户 / 禁言用户 / 封禁用户。
- 处置需要填写理由，并写入审计记录。
- 处置结果会通知：被处置用户（必须）；举报人（可选，MVP 建议支持）。

#### Scenario: BBS-MOD-101-3 Audit & Traceability
所有治理动作可追溯：
- 记录“谁在何时对什么做了什么”，包含理由与前后状态。
- 支持按目标/操作者/时间范围检索（MVP 可只提供后台筛选）。

#### Scenario: BBS-MOD-101-4 Auto-hide by Threshold (Optional)
当同一目标被多个不同用户举报达到阈值：
- 系统可自动将内容临时隐藏，并进入人工复核队列。

### Requirement: BBS-MOD-102 Blocklist & Anti-Harassment
**Module:** social-service / frontend / content-service / message-service

#### Scenario: BBS-MOD-102-1 Block Rules
用户 A 拉黑用户 B 后：
- A 侧不再展示 B 的帖子/评论（列表与详情一致）。
- A 与 B 互相禁止私信。
- B 不能对 A 的帖子/评论进行评论/回复（MVP）。

#### Scenario: BBS-MOD-102-2 Moderator Bypass
版主/管理员用于治理目的的查看与处置不受拉黑影响：
- 治理后台可访问被拉黑用户的内容与举报详情。

### Requirement: BBS-CONT-201 Edit & Soft Delete
**Module:** content-service / frontend / gateway

#### Scenario: BBS-CONT-201-1 Edit Post within 24h
帖子作者在发帖后 24 小时内可编辑标题/正文/标签/分类：
- 超过窗口后禁止编辑（给出明确错误提示）。
- 被编辑内容需展示“已编辑”时间。

#### Scenario: BBS-CONT-201-2 Edit Comment within 15m
评论作者在发布后 15 分钟内可编辑内容：
- 超过窗口后禁止编辑（给出明确错误提示）。
- 被编辑评论展示“已编辑”标识。

#### Scenario: BBS-CONT-201-3 Soft Delete and Origin Tracking
删除采用软删除：
- 作者删除：对外隐藏，但后台可追溯删除来源为“作者”。
- 版主/管理员删除：保留现有管理入口与权限，并记录删除理由与来源。

### Requirement: BBS-BOOK-301 Bookmark Posts
**Module:** content-service / frontend

#### Scenario: BBS-BOOK-301-1 Bookmark Toggle + Status
登录用户可对帖子收藏/取消收藏：
- 详情页可见收藏状态（已收藏/未收藏）。
- 收藏关系为私密（默认不展示给其他人）。

#### Scenario: BBS-BOOK-301-2 My Bookmarks List
用户可查看“我的收藏”：
- 支持分页与按时间排序（MVP：按收藏时间倒序）。
- 列表可跳转到帖子详情。

### Requirement: BBS-SUB-302 Subscribe Taxonomy
**Module:** content-service / frontend

#### Scenario: BBS-SUB-302-1 Subscribe Categories
用户可订阅/取消订阅分类（category）：
- 在分类入口处提供订阅按钮与订阅状态展示。

#### Scenario: BBS-SUB-302-2 Subscribe Tags (Optional)
用户可订阅/取消订阅标签（tag）：
- 在标签入口或帖子标签区域提供订阅按钮。

#### Scenario: BBS-SUB-302-3 Filter by Subscriptions
帖子列表支持“仅看订阅”筛选：
- 输出范围限定为用户订阅的分类/标签集合内的帖子。

### Requirement: BBS-GROW-401 Points & Level
**Module:** user-service / common / frontend

#### Scenario: BBS-GROW-401-1 Points by Events with Anti-abuse
积分增长采用事件驱动：
- 发帖/评论/获赞触发积分增量。
- 具备最小防刷：每日积分上限；同一用户对同一作者的点赞贡献积分上限（MVP 允许简化）。

#### Scenario: BBS-GROW-401-2 Profile Shows Score & Level
个人主页显示积分与等级：
- 等级可由积分映射计算，或以规则计算为准。

#### Scenario: BBS-GROW-401-3 Leaderboard
提供全站榜单：
- 至少提供“总积分 Top N”（周/月榜可后置）。

## Risk Assessment
- **Risk:** 权限与治理能力引入带来误封/误删风险；跨服务联动（处罚/通知/积分）增加一致性与排障复杂度；新增写路径与表结构可能引入性能热点与数据回滚难题。
- **Mitigation:** 按 MVP 分阶段交付；所有治理动作强制记录审计与理由；关键接口加限流与输入校验；事件消费做幂等与 DLQ；上线前用集成测试覆盖“举报→处置→通知/限制生效”的端到端路径。
