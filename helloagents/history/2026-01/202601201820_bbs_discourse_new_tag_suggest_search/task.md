# Task List：Discourse 体验增强（新内容提示条 + 标签自动补全 + 搜索联动 taxonomy）

Directory: `helloagents/plan/202601201820_bbs_discourse_new_tag_suggest_search/`

---

## 1. content-service：tags suggest
- [√] 1.1 增加 `GET /api/tags/suggest?q=&limit=`（前缀匹配 + 上限保护）
- [√] 1.2 为 tags suggest 补齐 MyBatis Mapper/DAO 与单测

## 2. common/content-service：事件与 internal scan 补齐 taxonomy
- [√] 2.1 扩展 `common` 的 `PostPayload`：新增 `categoryId/tags`
- [√] 2.2 content-service 写路径与 moderation/update：发布事件时补齐 `categoryId/tags`
- [√] 2.3 internal scan `/internal/content/posts` 返回补齐 taxonomy 字段（批量取 tags，避免 N+1）

## 3. search-service：索引与过滤
- [√] 3.1 ES 文档 `EsPostDocument` 增加 `categoryId/tags`
- [√] 3.2 search API 支持 `categoryId/tag` 参数并落到 repo 过滤逻辑
- [√] 3.3 更新 search-service 测试（SearchControllerTest）

## 4. frontend：Discourse 新内容条 + composer tag autocomplete + search filters
- [√] 4.1 PostsView：新增“新内容提示条 + 上次看到这里分割线”（order=latest 生效）
- [√] 4.2 PostsView composer：标签输入升级为 chip + 自动补全（调用 tags suggest）
- [√] 4.3 FeedToolbar/SearchView：taxonomy 过滤 UI 与 URL query SSOT 对齐

## 5. 安全检查
- [√] 5.1 校验 tags 输入（前端提示 + 后端兜底）；公共 GET 放行路径复核

## 6. 文档与归档
- [√] 6.1 更新知识库：`helloagents/wiki/modules/frontend.md`、`helloagents/wiki/modules/content.md`、`helloagents/wiki/modules/search.md`（如存在）与 `helloagents/wiki/data.md`（如需）
- [√] 6.2 更新 `helloagents/CHANGELOG.md`
- [√] 6.3 迁移方案包到 `helloagents/history/YYYY-MM/` 并更新 `helloagents/history/index.md`

## 7. 测试
- [√] 7.1 `mvn -pl content-service -am test`
- [√] 7.2 `mvn -pl search-service -am test`
- [√] 7.3 `npm -C frontend test && npm -C frontend run build`
