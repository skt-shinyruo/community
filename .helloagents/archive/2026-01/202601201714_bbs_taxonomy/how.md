# 技术设计：BBS 标签/分类（Taxonomy）全链路落地

## 技术方案

### 核心技术
- Backend：Spring Boot + MyBatis（沿用现有 content-service 技术栈）
- DB：MySQL（deploy 初始化）+ H2（测试，MySQL mode）
- Frontend：Vue 3 + 现有 UI 组件/Design Tokens（不引入新的大型 UI 框架）

### 实现要点
1. **数据模型**
   - `discuss_post.category_id`：帖子所属分类（可空）
   - `category`：分类字典（含 position，用于排序）
   - `tag`：标签字典（唯一）
   - `post_tag`：帖子-标签多对多关联（复合主键，便于去重）
2. **写路径（发帖）**
   - 先写 `discuss_post`，获得 postId
   - tags 归一化、去重、数量限制
   - 对每个 tag：select-or-insert（必要时处理唯一键冲突），再写 `post_tag`
   - 全流程纳入同一事务（PostCommandService）
3. **读路径（列表/详情）**
   - `GET /api/posts` 支持 `categoryId` / `tag` 过滤
   - 批量查询 tags：按 postIds 批量 join `post_tag + tag`，组装到响应，避免 N+1
   - 分类列表 `GET /api/categories` 返回 postCount（聚合/左连接）
   - 热门标签 `GET /api/tags/hot` 返回 useCount（按 post_tag 聚合）
4. **前端路由 SSOT**
   - posts 列表 query 扩展：`categoryId`、`tag`
   - 右侧面板与列表内的分类/标签点击都通过改写 query 触发筛选

## API 设计

### GET /api/categories
- **用途**：获取分类列表（用于右侧面板、发帖下拉、列表展示映射）
- **返回**：`[{ id, name, description, position, postCount }]`

### GET /api/tags/hot?limit=
- **用途**：获取热门标签 Top-N
- **返回**：`[{ name, useCount }]`

### POST /api/posts
- **用途**：创建帖子（可选 categoryId + tags）
- **请求**：`{ title, content, categoryId?, tags? }`
- **约束**：
  - tags 最大 5 个
  - 单个 tag 长度限制（服务端校验）

### GET /api/posts
- **用途**：帖子列表（支持排序 + taxonomy 过滤）
- **参数**：
  - `order=latest|hot`
  - `page` / `size`
  - `categoryId`（可选）
  - `tag`（可选）

## 数据模型

### discuss_post（新增）
- `category_id int null`
- 索引：`idx_discuss_post_category_id(category_id)`

### category（新增）
- `id int pk auto_increment`
- `name varchar(64) unique`
- `description varchar(255)`
- `position int`
- `create_time timestamp`

### tag（新增）
- `id int pk auto_increment`
- `name varchar(64) unique`
- `create_time timestamp`

### post_tag（新增）
- `post_id int`
- `tag_id int`
- `create_time timestamp`
- 主键：`(post_id, tag_id)`
- 索引：`idx_post_tag_tag_id(tag_id)`、`idx_post_tag_post_id(post_id)`

## 安全与性能
- 安全：
  - 发帖写入 tags/category 前做输入校验与归一化，避免超长/空值/异常字符
  - `GET /api/categories`、`GET /api/tags/hot` 作为公共信息接口，gateway 与 content-service 统一放行 GET
- 性能：
  - posts 列表按标签过滤使用 `exists` + 索引避免大 join
  - tags 按 postIds 批量查询，避免前端/后端 N+1

## 测试与部署
- 测试：
  - content-service MockMvc：新增分类/热门标签公共访问测试
  - 发帖带 tags/category 的链路测试：创建→列表/详情可见→热门标签聚合可见
- 部署：
  - deploy MySQL init：增加补列/补表逻辑，兼容已存在表的环境
  - gateway 路由增加 `Path=/api/categories/**,/api/tags/**`

