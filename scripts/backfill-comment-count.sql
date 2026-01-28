-- 一次性修复 discuss_post.comment_count（按 comment 表重算）
-- 使用方式：在业务低峰执行；可按 id 范围分批执行以降低锁竞争。

update discuss_post p
left join (
  select entity_id as post_id, count(*) as cnt
  from comment
  where status = 0
    and entity_type = 1
  group by entity_id
) c on p.id = c.post_id
set p.comment_count = coalesce(c.cnt, 0);

