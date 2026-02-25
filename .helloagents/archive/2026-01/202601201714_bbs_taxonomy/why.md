# 变更提案：BBS 标签/分类（Taxonomy）全链路落地

## 需求背景
当前帖子体系只有“帖子/评论”两类核心内容，缺少 BBS/论坛常见的“分类（Category）/标签（Tag）”结构化字段：
- 前端无法像 Discourse 一样在列表中快速识别主题归属（分类）与主题关键词（标签）
- 右侧面板的“热门标签”只能用静态关键词模拟，无法随社区内容演化
- 列表只能按“最新/热门/未读/置顶/精华”做轻量筛选，无法按分类/标签进行主题聚合

本次变更目标：把标签/分类做成真实后端字段，并让前端列表、发帖、右侧面板、筛选链路全部打通，整体风格继续向“GitHub Discussions × Discourse（更 Discourse）”收敛。

## 变更内容
1. 数据模型增加：Category、Tag、Post-Tag 关联表；DiscussPost 增加 categoryId
2. 内容服务 API 增强：分类列表、热门标签、发帖写入分类/标签、帖子列表按分类/标签过滤
3. 前端 UI 增强：发帖支持选择分类与输入标签；列表展示分类/标签并可一键过滤；右侧面板展示分类与热门标签（来自后端聚合）

## 影响范围
- **模块：**
  - content-service（数据模型/API/写路径）
  - gateway（路由与公共 GET 放行）
  - frontend（发帖/列表/侧栏/路由 query）
  - deploy（MySQL 初始化脚本）
  - helloagents（知识库同步与变更记录）
- **数据：**
  - `community_content.discuss_post` 新增 `category_id`
  - 新增表：`category`、`tag`、`post_tag`
- **API：**
  - `GET /api/categories`
  - `GET /api/tags/hot?limit=`
  - `GET /api/posts` 增加 `categoryId` / `tag` 过滤参数
  - `POST /api/posts` 支持 `categoryId` 与 `tags[]`

## 核心场景

### Requirement: 发帖可选分类/标签
**Module:** content-service / frontend

#### Scenario: 用户发帖时选择分类并添加标签
- 期望：帖子成功创建；详情与列表可看到分类/标签
- 期望：标签去空/去重/数量限制；非法输入有明确报错

### Requirement: 列表可按分类/标签筛选
**Module:** content-service / frontend

#### Scenario: 用户从右侧面板点击分类/标签进入过滤列表
- 期望：URL query 作为 SSOT；刷新/分享链接可复现筛选条件
- 期望：列表展示结构化信息（分类/标签）且不破坏紧凑密度

### Requirement: 右侧面板展示热门标签（后端聚合）
**Module:** content-service / frontend

#### Scenario: 右侧面板显示 Top-N 热门标签并可点击筛选
- 期望：热门标签来自后端按使用次数聚合；支持 limit 参数

## 风险评估
- **风险：DB 迁移兼容性**：已有环境可能已存在 `discuss_post` 表，单纯 `create table if not exists` 不会补列
  - **缓解**：MySQL init 脚本增加“安全补列/补表”逻辑（基于 information_schema 判断后再执行）
- **风险：接口兼容性**：前端与其它调用方可能仍按旧请求体发帖
  - **缓解**：`categoryId`/`tags` 设计为可选字段；旧客户端不受影响
- **风险：性能**：列表按标签过滤涉及 exists/join
  - **缓解**：为 `post_tag(tag_id/post_id)` 建索引；列表查询以 exists 为主，避免大 join

