# content

## Purpose
提供帖子、评论与回复等内容域能力，并包含敏感词过滤与热帖排序相关逻辑。

## Module Overview
- **Responsibility：** 发帖；帖子详情；评论/回复；敏感词过滤；热帖分数刷新；与搜索/通知联动
- **Status：** ✅Stable
- **Last Updated：** 2026-01-16

## Specifications

### Requirement: 发帖与浏览帖子
**Module:** content
用户发布帖子后可在首页与详情页浏览。

#### Scenario: 发布帖子
前置条件：用户已登录
- 发布成功
- 触发发帖事件（用于搜索索引/通知等）

#### Scenario: 浏览帖子详情
前置条件：帖子存在
- 返回帖子、作者、评论/回复列表
- 返回点赞数量与点赞状态

### Requirement: 评论与回复
**Module:** content
支持对帖子评论与对评论回复。

#### Scenario: 对帖子发表评论
前置条件：用户已登录
- 评论写入数据库
- 更新帖子评论数
- 触发评论事件（通知）

### Requirement: 敏感词过滤
**Module:** content
帖子标题/内容、评论内容需做敏感词过滤。

#### Scenario: 发布包含敏感词的内容
- 敏感词被替换为 `***`

### Requirement: 热帖排序
**Module:** content
根据精华/评论数/点赞数/时间计算帖子分数，并周期刷新。

#### Scenario: 帖子被点赞/评论后进入刷新集合
- Redis `post:score` 集合写入帖子 ID
- Quartz 任务周期刷新分数并同步到搜索

## API Interfaces（现状）
- `POST /discuss/add`
- `GET /discuss/detail/{discussPostId}`
- `POST /comment/add/{discussPostId}`
- 管理接口：`POST /discuss/top`、`POST /discuss/wonderful`、`POST /discuss/delete`

## Data Models
### discuss_post
（详见 `helloagents/wiki/data.md` 的 “discuss_post” 小节）

### comment
（详见 `helloagents/wiki/data.md` 的 “comment” 小节）

## Dependencies
- user（作者信息）
- social（点赞信息/状态）
- message（评论/点赞/关注通知）
- search（发帖/删帖事件同步索引）
- infra（Quartz、Redis、Kafka）

## Change History
- （暂无）
