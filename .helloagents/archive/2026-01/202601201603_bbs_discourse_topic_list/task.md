# 任务清单：bbs_discourse_topic_list（Development Implementation / Lightweight Iteration）

目标：在现有「GitHub Discussions × Discourse」基础上进一步向 Discourse 靠拢：
- 列表列布局（Title / Replies / Likes / Activity）
- 未读状态（本地追踪：按“最后活动时间”判断）
- 最后回复信息（最后回复人 + 时间）

## Tasks

- [√] content-service：为 `/api/posts` 列表补齐 `lastReplyUserId/lastReplyTime/lastActivityTime`（基于 comment 表计算）
- [√] frontend：Posts 列表改为 Discourse-like 列布局（包含表头）
- [√] frontend：实现未读状态（localStorage 追踪已读时间 + lastActivityTime 比较）
- [√] frontend：展示最后回复信息（avatar + username + time），并复用现有用户缓存
- [√] 质量验证：`npm -C frontend test` + `npm -C frontend run build` + `mvn -pl content-service -am test`
