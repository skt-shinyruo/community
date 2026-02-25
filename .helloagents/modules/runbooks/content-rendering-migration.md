# Runbook：内容渲染契约迁移（避免二次转义）

## 背景
历史实现曾在写入阶段对帖子/评论做全量 HTML escape（如 `<` -> `&lt;`），前端 Markdown 渲染再 escape 后通过 `v-html` 输出，导致用户看到 `&amp;lt;` 这类“可见转义符”。

本仓库当前采用“兼容读 + 最小化写”的渐进方案：
- **对外契约**：API 返回 `title/content` 为“text 语义”（展示侧负责 escape + Markdown 白名单渲染）
- **写入**：不再做全量 htmlEscape；仅对 `&` 做最小化 escape（避免用户输入 literal entity 在读路径解码后语义变化）
- **读出**：对历史数据做一次性、白名单、单次 HTML entity 解码（可配置开关）

## 配置开关（content-service）
- `content.render.legacy-entity-unescape-enabled`（默认 true）
  - true：读路径对历史 entity（`&lt; &gt; &quot; &#39; &apos; &amp;`）做一次性白名单解码
  - false：不做兼容解码（适用于完成数据迁移后）
- `content.render.escape-ampersand-on-write-enabled`（默认 true）
  - true：写入阶段仅 `& -> &amp;`
  - false：写入阶段不做该最小化 escape（适用于完成数据迁移后）

建议：**在关闭 `legacy-entity-unescape-enabled` 的同时再评估是否关闭 `escape-ampersand-on-write-enabled`**，避免出现 `&amp;` 在页面可见的体验回退。

## 推荐发布顺序（灰度/渐进）
1) **阶段 A（读兼容先行）**
   - 保持默认：`legacy-entity-unescape-enabled=true`
   - 观察：帖子/评论展示是否从 `&amp;lt;` 恢复为预期文本；错误率是否变化

2) **阶段 B（写入契约收敛）**
   - 保持默认：`escape-ampersand-on-write-enabled=true`
   - 新写入内容不再产生“全量 escape”导致的二次转义

3) **阶段 C（可选：一次性数据修复 + reindex）**
   - 目标：把历史数据从 entity 形式迁移为“原文（text）”，彻底移除兼容成本
   - 强制前置：**先备份**（MySQL 备份 + 记录变更窗口）
   - 迁移范围（示例）：
     - `community_content.discuss_post.title/content`
     - `community_content.comment.content`
   - 迁移完成后：执行搜索索引重建（reindex），保证搜索侧一致（参考 `scripts/search-reindex.sh` 与 Ops Console）

4) **阶段 D（关闭兼容开关）**
   - 设置 `content.render.legacy-entity-unescape-enabled=false`
   - （如确认无需保留 literal entity 语义保护）再设置 `content.render.escape-ampersand-on-write-enabled=false`

## 事故回滚思路（保守）
- 若出现展示异常：
  - 首先回到阶段 A 的配置（开启 `legacy-entity-unescape-enabled`），快速恢复“读正确”
  - 若已做数据迁移：优先用备份回滚（避免二次迁移叠加）

## 验收清单（建议）
- [ ] 新发帖/新评论包含 `& < >`、代码块、链接时展示正常
- [ ] 历史内容不再出现 `&amp;lt;` 等二次转义可见问题
- [ ] `<script>` 等注入不会执行（展示为文本）
- [ ] 搜索页高亮仍只允许 `<em>`（无其他标签注入）

