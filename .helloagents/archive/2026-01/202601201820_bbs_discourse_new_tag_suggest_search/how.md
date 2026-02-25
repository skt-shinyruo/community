# 技术设计：Discourse 体验增强（新内容提示条 + 标签自动补全 + 搜索联动 taxonomy）

## 技术方案概览

### 方案 A（采用）：在现有架构上“补齐字段 + 轻量交互”
- 新内容提示条/分割线：继续使用 `readTracker.touchPostsListSeen()` 的 baseline（本地上次访问时间），按 `lastActivityTime` 计算新内容区间并渲染分割线。
- 标签自动补全：content-service 新增 `GET /api/tags/suggest`（前缀匹配 + 热门兜底），前端 composer 做 chip 输入与 datalist/下拉提示。
- 搜索联动 taxonomy：扩展 `common.PostPayload` 增加 `categoryId/tags`，content-service 在事件与 internal scan 中补齐；search-service 索引落地并支持过滤参数。

### 关键理由
- 不引入重型 UI/状态系统，保持现有“克制、耐看、信息密度高”的设计风格
- taxonomy 字段通过事件与 internal scan 同步，避免 search-service 额外回源调用导致 N+1

## API 设计

### GET /api/tags/suggest
- **参数：** `q`（前缀/包含关键字）、`limit`（默认 8，上限 20）
- **返回：** `[{ name, useCount? }]`（建议仅返回 name，useCount 可选）
- **策略：**
  - 先按 name 前缀匹配（like 'q%'/或 contains，视 SQL 选择）
  - 不足时用热门标签补齐（与 `/api/tags/hot` 共享聚合逻辑）

### GET /api/search/posts（增强）
- **新增参数：** `categoryId`、`tag`
- **行为：**
  - keyword 为空：返回全部（可叠加 taxonomy 过滤）
  - keyword 非空：title/content 关键字匹配，并叠加 taxonomy 过滤

## 数据与索引

### common PostPayload 扩展
- 新增字段：`categoryId(Integer)`、`tags(List<String>)`
- 用途：PostPublished/PostUpdated 事件与 internal scan 复用同一 payload，保证 search-service 获取到 taxonomy。

### search-service ES 文档扩展
- `categoryId`：数值字段
- `tags`：keyword 数组（用于精确过滤）

## 前端交互

### PostsView 新内容提示条与分割线
- 仅在 `order=latest` 时显示（避免“热门”排序下插入时间线造成语义混乱）
- 提示条：显示“自上次访问后新增 X 条”，提供：
  - “跳到上次位置”（滚动到分割线）
  - “收起”（仅隐藏本次提示，不影响下次）
- 分割线：插入到“新内容”与“旧内容”之间，文案“上次看到这里”

### Composer 标签输入（chip）
- 单个输入框：输入一个标签，Enter/逗号确认加入
- 显示已选 tags（最多 5 个），支持移除
- 自动补全：从 `/api/tags/suggest` 拉取建议；fallback 使用 `/api/tags/hot`

### SearchView taxonomy 过滤
- 分类 select + tag input
- URL query：`q`（keyword）、`categoryId`、`tag`
- 搜索结果展示 tag/category（可点击跳转到 posts 过滤）

## 测试策略
- content-service：新增 controller test 覆盖 `GET /api/tags/suggest`（公共访问 + 基本匹配）
- search-service：扩展 SearchControllerTest 覆盖 taxonomy 参数能正常透传并返回（至少不 500）
- frontend：扩展 navigation/query 单测（若新增 SSOT 逻辑）并确保 `npm test/build` 通过

