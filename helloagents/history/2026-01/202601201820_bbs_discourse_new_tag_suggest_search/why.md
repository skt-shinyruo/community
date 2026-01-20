# 变更提案：Discourse 体验增强（新内容提示条 + 标签自动补全 + 搜索联动 taxonomy）

## 需求背景
当前 BBS UI 已基本收敛到「GitHub Discussions × Discourse」的列表与信息架构，但仍有三个关键体验缺口：

1) **“新内容/上次看到这里”缺失**  
目前仅有本地未读点（基于 `lastActivityTime` + `localStorage`），缺少 Discourse 标志性的“上次访问后新增内容”提示与分割线，导致回访用户需要自己判断从哪里继续看。

2) **发帖标签输入缺少自动补全**  
虽然已支持 `tags[]` 写入后端，但发帖端仍是自由文本输入，缺少“标签建议/规范提示/快捷添加”的论坛式体验，且容易导致标签分裂（大小写/空格/格式不一致）。

3) **搜索未联动 taxonomy**  
taxonomy 已落地到 content-service 与前端列表，但 search-service 的索引与搜索 API 仍只支持关键词检索（title/content），无法按分类/标签过滤与展示，导致“标签/分类”无法覆盖从浏览→搜索的完整闭环。

本次目标：在不引入重型 UI 框架、保持“克制耐看”的前提下，把以上 3 个体验补齐，使整体更像 Discourse。

## 变更内容
1. posts 列表页增加 Discourse 风格的“新内容提示条 + 上次看到这里分割线”（基于现有本地 baseline 机制）
2. taxonomy 增加 tags suggest API：支持发帖标签输入的自动补全与规范化提示
3. search-service 索引补齐 `categoryId/tags[]`，并支持搜索页按分类/标签过滤（前端 UI 对接）

## 影响范围
- **模块：**
  - content-service（tags suggest API；内部扫描/事件 payload 补齐 taxonomy 字段）
  - common（PostPayload 扩展 taxonomy 字段，供事件与 reindex 复用）
  - search-service（索引字段与搜索过滤）
  - frontend（PostsView 新内容条/分割线；发帖标签自动补全；SearchView taxonomy 过滤 UI）
  - gateway（若新增路径需要补齐放行/路由；本次预计复用既有 `/api/tags/**` 路由）
- **API：**
  - `GET /api/tags/suggest?q=&limit=`（新增）
  - `GET /api/search/posts` 增加 `categoryId` / `tag` 参数（增强）
- **数据/索引：**
  - ES 文档增加 `categoryId`、`tags[]` 字段（需要重建索引才能生效）

## 核心场景

### Requirement: 新内容提示条与分割线
**Module:** frontend

#### Scenario: 用户再次打开帖子列表
- 期望：展示“自上次访问后新增 X 条”，并能定位到“上次看到这里”
- 期望：仅在“最新”视图（以及 taxonomy 过滤）中生效；“热门”视图不强行插入时间分割线

### Requirement: 发帖标签自动补全
**Module:** content-service / frontend

#### Scenario: 用户输入标签时出现建议，并能一键加入
- 期望：建议来源于后端已有 tag（按前缀匹配）与热门标签（兜底）
- 期望：前端即时提示规范；后端最终校验与归一化兜底（最多 5 个、单个标签长度/字符集约束）

### Requirement: 搜索联动 taxonomy
**Module:** search-service / frontend

#### Scenario: 搜索结果可按分类/标签过滤
- 期望：搜索页 URL query 可携带 `categoryId/tag` 并可分享复现
- 期望：搜索结果可展示分类/标签信息并支持跳转到 posts 过滤

## 风险评估
- **索引映射变更风险**：ES index 增加新字段，需要 reindex 才能生效
  - **缓解**：复用现有 reindex 能力；文档中提示需要重建索引
- **事件 payload 兼容性**：PostPayload 扩展字段可能影响下游
  - **缓解**：字段为可选；对旧逻辑保持默认 null/空数组；新增字段仅补齐不改旧字段语义
- **前端复杂度增长**：标签输入从“单输入框”变为“chip 输入”
  - **缓解**：保持实现轻量，优先原生交互（Enter/逗号分隔），不引入新依赖

