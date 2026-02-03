# content

## Purpose
提供帖子、评论与回复等内容域能力，并包含敏感词过滤与热帖排序相关逻辑。

## Module Overview
- **Responsibility：** 发帖；帖子详情；评论/回复；敏感词过滤；热帖分数刷新；与搜索/通知联动
- **Status：** ✅Stable
- **Last Updated：** 2026-02-02

## Specifications

### Requirement: 发帖与浏览帖子
**Module:** content
用户发布帖子后可在首页与详情页浏览。

### Requirement: 内容渲染契约（store-raw + render-safe）
**Module:** content
目标：避免二次转义，同时保持 XSS 安全与历史数据兼容（不做全量 DB 迁移也能读对）。

约定（推荐理解方式）：
- API 返回 `title/content` 为“text 语义”（展示侧负责 escape + Markdown 白名单渲染）
- 历史数据兼容：对外响应/事件 payload 在读路径做一次性基础 entity 解码（白名单、单次）
- 写入阶段：不再做全量 htmlEscape；仅按配置对 `&` 做最小化 escape（防止用户输入 literal entity（如 `&lt;`）在解码后语义变化）

配置开关（content-service）：
- `content.render.legacy-entity-unescape-enabled`（默认 true）：读路径对历史 entity 进行基础解码
- `content.render.escape-ampersand-on-write-enabled`（默认 true）：写入阶段仅对 `&` 做最小化 escape

Runbook：
- `helloagents/wiki/runbooks/content-rendering-migration.md`

#### Scenario: 发布帖子
前置条件：用户已登录
- 发布成功
- 触发发帖事件（用于搜索索引/通知等；默认走 Outbox：事务内写 outbox + relay 投递；应急情况下可切回 After-Commit 发送）

#### Scenario: 浏览帖子详情
前置条件：帖子存在
- 返回帖子、作者、评论/回复列表
- 返回点赞数量与点赞状态（读 Redis `like:entity:*` 投影；由 social 事件驱动维护，允许短暂最终一致窗口）

### Requirement: 评论与回复
**Module:** content
支持对帖子评论与对评论回复。

#### Scenario: 对帖子发表评论
前置条件：用户已登录
- 评论写入数据库
- 更新帖子评论数
- 触发评论事件（通知；支持 Outbox 模式；未启用时仍走 After-Commit 发送）

### Requirement: 敏感词过滤
**Module:** content
帖子标题/内容、评论内容需做敏感词过滤。

#### Scenario: 发布包含敏感词的内容
- 敏感词被替换为 `***`

### Requirement: 热帖排序
**Module:** content
根据精华/评论数/点赞数/时间计算帖子分数，并周期刷新。

#### Scenario: 帖子被点赞/评论后进入刷新集合
- Redis `post:score` 集合写入帖子 ID（在 DB 事务提交后执行，避免回滚仍触发刷新）
- Quartz 任务周期刷新分数并同步到搜索；刷新失败会回补重试（至少一次语义），避免异常导致 postId 永久丢失

### Requirement: 内容生命周期（编辑/软删）
**Module:** content
提供“作者可编辑窗口 + 作者软删”能力，配合治理处置记录形成可追溯链路。

#### Scenario: 作者编辑帖子（窗口期）
- 默认规则：发布后 24h 内允许编辑（超出窗口返回 4xx）
- 更新 `update_time/edit_count`，并发布 `PostUpdated` 事件（用于搜索/旁路一致性）

#### Scenario: 作者编辑评论（窗口期）
- 默认规则：发布后 15min 内允许编辑（超出窗口返回 4xx）
- 更新 `update_time/edit_count`

#### Scenario: 作者删除帖子（软删）
- 写入 `deleted_by/deleted_reason/deleted_time`（不物理删除），并发布 `PostDeleted` 事件

### Requirement: 举报与治理闭环
**Module:** content
支持举报（对帖子/评论/用户）与治理处置（审核、执行、留痕）。

#### Scenario: 用户举报
- 生成 `report` 记录（OPEN）

#### Scenario: 版主处置
- 生成 `moderation_action` 审计记录
- 需要时调用 `user-service` internal API 执行禁言/封禁

### Requirement: 收藏与订阅
**Module:** content
提供收藏帖子、订阅分类与“仅看订阅”筛选的基础能力。

## API Interfaces（现状）
- `GET /api/categories`（分类列表；包含 `postCount` 用于侧栏展示）
- `GET /api/tags/hot?limit=`（热门标签 Top-N；返回 `useCount` 聚合值）
- `GET /api/tags/suggest?q=&limit=`（标签建议：前缀匹配 + 热门兜底；用于发帖/搜索的自动补全）
- `GET /api/posts`（order=latest|hot；支持 `categoryId`/`tag` 过滤；列表返回补齐 `lastReplyUserId/lastReplyTime/lastActivityTime`，用于 Discourse-like topic list：活动列与未读判断）
- `POST /api/posts`（敏感词过滤 + 最小化 escape（仅 `&`，可配置）+ 发布 PostPublished；请求体支持可选 `categoryId`/`tags[]`）
- `PUT /api/posts/{postId}`（作者 24h 内编辑；发布 PostUpdated；更新 `update_time/edit_count`）
- `DELETE /api/posts/{postId}`（作者软删；发布 PostDeleted；写入 `deleted_*`）
- `GET /api/posts/{postId}`（包含 likeCount/liked；返回 `categoryId`/`tags[]`）
- `GET /api/posts/{postId}/comments`、`POST /api/posts/{postId}/comments`（敏感词过滤 + 最小化 escape（仅 `&`，可配置）+ 发布 CommentCreated）
- `PUT /api/posts/{postId}/comments/{commentId}`（作者 15min 内编辑；更新 `update_time/edit_count`）
- `POST /api/reports`（提交举报：POST/COMMENT/USER）
- `GET /api/moderation/reports`（治理后台：举报列表；仅 MOD/ADMIN）
- `POST /api/moderation/actions`（治理后台：处置动作；仅 MOD/ADMIN；发布 ModerationActionApplied）
- `GET /api/moderation/actions`（治理后台：处置审计查询；仅 MOD/ADMIN）
- `PUT /api/posts/{postId}/bookmark`、`DELETE /api/posts/{postId}/bookmark`（收藏/取消收藏）
- `GET /api/bookmarks`（我的收藏列表）
- `PUT /api/categories/{categoryId}/subscribe`、`DELETE /api/categories/{categoryId}/subscribe`（订阅/取消订阅分类）
- `GET /api/subscriptions/categories`（我的分类订阅列表）
- `GET /api/posts?subscribed=true`（仅看订阅：按订阅分类过滤）
- `GET /internal/content/posts`（内部接口：需要 `X-Internal-Token`；供 search-service 重建索引扫描帖子）
- `GET /internal/content/entities/resolve`（内部接口：需要 `X-Internal-Token`；供 social-service 在写路径解析 POST/COMMENT 的 owner/postId，构造可信事件 payload）
- `POST /internal/content/likes/backfill`（内部运维接口：默认关闭；回填 Redis 点赞投影；受 `X-Internal-Token` + ops-guard + endpoint-enabled 共同保护）

## Data Models
### discuss_post
（详见 `helloagents/wiki/data.md` 的 “discuss_post” 小节）

### category / tag / post_tag
（详见 `helloagents/wiki/data.md` 的 taxonomy 小节：`category`、`tag`、`post_tag`）

### comment
（详见 `helloagents/wiki/data.md` 的 “comment” 小节）

### report / moderation_action / post_bookmark / user_subscription_category
（详见 `helloagents/wiki/data.md` 对应小节）

## Dependencies
- user（作者信息）
- social（点赞 SSOT 与事件来源；由 social 事件驱动维护 content 的 Redis 点赞投影）
- social-service internal（likes scan：用于冷启动/纠偏回填 Redis 点赞投影）
- social-service internal（拉黑关系：评论/回复写路径前置校验）
- user-service internal（禁言/封禁状态：发帖/评论写路径前置校验；治理动作落地）
- message（评论/点赞/关注通知）
- search（发帖/删帖事件同步索引）
- infra（Quartz、Redis、Kafka）

## Change History
- 2026-01-18：写路径事件发布改为 After-Commit（避免 DB 回滚仍发事件），并将热度刷新 enqueue 延后到事务提交后执行。
- 2026-01-19：补充内部帖子扫描接口（`/internal/content/posts`），用于支持 search-service 在严格 schema 隔离下完成 reindex 冷启动。
- 2026-01-20：`/api/posts` 列表返回补齐“最后回复/最后活动”字段（包含评论与回复评论），支撑前端 Discourse 风格 topic list（活动列 + 未读提示）。
- 2026-01-20：引入 taxonomy（分类/标签）：新增 `category/tag/post_tag` 表，发帖支持 `categoryId/tags[]`，列表支持按分类/标签过滤，支撑 Discourse-like 信息架构与侧栏聚合。
- 2026-01-20：新增 `GET /api/tags/suggest` 标签建议接口，支撑 Discourse-like 标签输入体验（autocomplete）。
- 2026-01-23：新增举报/治理闭环（report/moderation_action + /api/reports + /api/moderation/**），补齐内容生命周期（编辑窗口/作者软删）、收藏/订阅与“仅看订阅”。
- 2026-02-01：Outbox 默认开启（可靠投递作为默认安全态），并补齐 relay 的 SENDING 回收 + SENT 清理与索引。
- 2026-02-01：消费 `LikeCreated/LikeRemoved` 维护 Redis `like:entity:*` 投影，帖子详情与热帖分数读源一致，且支持取消点赞回落。
- 2026-02-01：评论/回复写路径拉黑校验改为“投影优先 + 缺失回源 + 回填”，消除投影缺失 fail-open 窗口。
- 2026-02-01：新增 internal entity resolve 与 likes backfill 运维入口，用于跨域写路径契约可信与冷启动纠偏。
- 2026-02-02：内容渲染契约收敛：写入停止全量 htmlEscape（仅对 `&` 最小化 escape 可配置），读路径对历史 entity 做一次性白名单解码（可配置），避免出现 `&amp;lt;` 二次转义可见问题，并保持 XSS 安全边界清晰。
- 2026-02-03：Outbox 认领升级支持 `FOR UPDATE SKIP LOCKED`（可配置回退），降低多实例 relay 并发时的锁等待与头阻塞风险；运维入口 `/internal/content/outbox/replay` 继续受 ops-guard（break-glass）保护。
