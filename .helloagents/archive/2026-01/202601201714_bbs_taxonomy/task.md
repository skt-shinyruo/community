# Task List：BBS 标签/分类（Taxonomy）全链路落地

Directory: `.helloagents/archive/2026-01/202601201714_bbs_taxonomy/`

---

## 1. 数据模型（DB）
- [√] 1.1 扩展 MySQL 初始化脚本：新增 category/tag/post_tag 表，且为 discuss_post 安全补列（category_id）
- [√] 1.2 同步 H2 测试 schema：补齐 discuss_post.category_id 与 taxonomy 表

## 2. content-service（API + 写路径）
- [√] 2.1 增加 Category/Tag/PostTag DAO + Mapper（MyBatis）
- [√] 2.2 增加 `GET /api/categories` 与 `GET /api/tags/hot` API（公共 GET）
- [√] 2.3 扩展 `POST /api/posts`：支持 `categoryId` 与 `tags[]`，并写入关联表
- [√] 2.4 扩展 `GET /api/posts` 与 `GET /api/posts/{id}`：返回 categoryId/tags 并支持过滤

## 3. gateway（路由与权限）
- [√] 3.1 content-service route 增加 `/api/categories/**,/api/tags/**`
- [√] 3.2 安全放行公共 GET：`GET /api/categories/**`、`GET /api/tags/**`

## 4. frontend（发帖/列表/侧栏）
- [√] 4.1 新增 taxonomy API client：categories 与 hot tags
- [√] 4.2 右侧面板：分类列表与热门标签改为后端数据，并跳转到 posts 过滤
- [√] 4.3 发帖：增加分类选择 + 标签输入（逗号/空格分隔），提交写入
- [√] 4.4 列表：展示分类/标签；点击分类/标签一键过滤；支持 query（categoryId/tag）作为 SSOT
- [√] 4.5 详情页：展示分类/标签信息（与列表保持一致）

## 5. 安全检查
- [√] 5.1 输入校验：tags/category 约束、SQL 注入风险、权限控制（公共 GET / 写接口鉴权）

## 6. 文档更新（知识库）
- [√] 6.1 更新 `.helloagents/data.md`：补齐 taxonomy 表结构
- [√] 6.2 更新模块文档：`.helloagents/modules/content.md`、`.helloagents/modules/frontend.md`、`.helloagents/modules/gateway.md`
- [√] 6.3 更新 `.helloagents/CHANGELOG.md`

## 7. 测试
- [√] 7.1 content-service：新增 MockMvc 测试覆盖 taxonomy API 与发帖写入链路
