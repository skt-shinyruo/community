-- ops_hardening_outbox_reindex - MySQL 8.0 schema migration
--
-- 目标：
-- - 修复/对齐 outbox_event 与 consumed_event* 的唯一约束与关键索引（避免重复副作用与扫表）
-- - 为“已有数据卷/已存在库”提供可回滚的迁移路径（先预检 → 再去重 → 再加约束/索引）
--
-- ⚠️ 注意：
-- - 本脚本包含潜在的破坏性操作（DELETE / ALTER TABLE）。
-- - 强烈建议在执行前完成备份（例如 scripts/mysql-backup.sh），并在低峰窗口执行。
-- - 默认仅提供“预检/准备”语句；实际 DELETE/DDL 请在确认后取消注释执行。

-- ============================================================
-- 0) 预检：确认版本与当前库
-- ============================================================
select version() as mysql_version;

-- ============================================================
-- A) community_content.outbox_event
-- ============================================================
use community_content;

select 'community_content.outbox_event duplicate(event_id) sample' as section;
select event_id, count(*) as cnt
from outbox_event
group by event_id
having cnt > 1
order by cnt desc
limit 20;

-- 若存在重复 event_id：先去重（保留最小 id），再加唯一约束/索引。
-- ⚠️ 取消注释前请确认：重复 event_id 代表重复投递/重复入库，删除较大 id 行不会丢失“唯一语义”所需信息。
-- delete t1
-- from outbox_event t1
-- join outbox_event t2
--   on t1.event_id = t2.event_id
--  and t1.id > t2.id;

-- 1) 确保 event_id 唯一约束存在（若已存在任意 unique 索引则跳过）
set @has_unique_outbox_event_id := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'outbox_event'
    and column_name = 'event_id'
    and non_unique = 0
);
set @sql := if(@has_unique_outbox_event_id = 0,
  'alter table outbox_event add unique key uk_outbox_event_id (event_id)',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 2) 确保 idx_outbox_status_next 为 (status, next_retry_at, id)
set @has_idx_outbox_status_next := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'outbox_event'
    and index_name = 'idx_outbox_status_next'
);
set @idx_outbox_status_next_has_id := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'outbox_event'
    and index_name = 'idx_outbox_status_next'
    and column_name = 'id'
    and seq_in_index = 3
);

-- 若索引存在但缺少 id（历史 drift），则 drop+recreate。
set @sql := if(@has_idx_outbox_status_next > 0 and @idx_outbox_status_next_has_id = 0,
  'drop index idx_outbox_status_next on outbox_event',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 若索引不存在或刚被 drop，则创建正确形态。
set @sql := if(@has_idx_outbox_status_next = 0 or (@has_idx_outbox_status_next > 0 and @idx_outbox_status_next_has_id = 0),
  'create index idx_outbox_status_next on outbox_event(status, next_retry_at, id)',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- ============================================================
-- B) community_message.consumed_event
-- ============================================================
use community_message;

select 'community_message.consumed_event duplicate(event_id) sample' as section;
select event_id, count(*) as cnt
from consumed_event
group by event_id
having cnt > 1
order by cnt desc
limit 20;

-- 若存在重复 event_id：先去重（保留最小 id），再加唯一约束。
-- delete t1
-- from consumed_event t1
-- join consumed_event t2
--   on t1.event_id = t2.event_id
--  and t1.id > t2.id;

-- 1) event_id 唯一约束（若已存在任意 unique 索引则跳过）
set @has_unique_consumed_event_id := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'consumed_event'
    and column_name = 'event_id'
    and non_unique = 0
);
set @sql := if(@has_unique_consumed_event_id = 0,
  'alter table consumed_event add unique key uk_consumed_event_id (event_id)',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 2) idx_consumed_event_at：应为 (consumed_at, id)
set @has_idx_consumed_event_at := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'consumed_event'
    and index_name = 'idx_consumed_event_at'
);
set @idx_consumed_event_at_has_id := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'consumed_event'
    and index_name = 'idx_consumed_event_at'
    and column_name = 'id'
    and seq_in_index = 2
);
set @sql := if(@has_idx_consumed_event_at > 0 and @idx_consumed_event_at_has_id = 0,
  'drop index idx_consumed_event_at on consumed_event',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql := if(@has_idx_consumed_event_at = 0 or (@has_idx_consumed_event_at > 0 and @idx_consumed_event_at_has_id = 0),
  'create index idx_consumed_event_at on consumed_event(consumed_at, id)',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- ============================================================
-- C) community_search.search_consumed_event
-- ============================================================
use community_search;

select 'community_search.search_consumed_event duplicate(event_id) sample' as section;
select event_id, count(*) as cnt
from search_consumed_event
group by event_id
having cnt > 1
order by cnt desc
limit 20;

-- 若存在重复 event_id：先去重（保留最小 id），再加唯一约束。
-- delete t1
-- from search_consumed_event t1
-- join search_consumed_event t2
--   on t1.event_id = t2.event_id
--  and t1.id > t2.id;

-- 1) event_id 唯一约束（若已存在任意 unique 索引则跳过）
set @has_unique_search_consumed_event_id := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'search_consumed_event'
    and column_name = 'event_id'
    and non_unique = 0
);
set @sql := if(@has_unique_search_consumed_event_id = 0,
  'alter table search_consumed_event add unique key uk_search_consumed_event_id (event_id)',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- 2) idx_search_consumed_at：应为 (consumed_at, id)
set @has_idx_search_consumed_at := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'search_consumed_event'
    and index_name = 'idx_search_consumed_at'
);
set @idx_search_consumed_at_has_id := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'search_consumed_event'
    and index_name = 'idx_search_consumed_at'
    and column_name = 'id'
    and seq_in_index = 2
);
set @sql := if(@has_idx_search_consumed_at > 0 and @idx_search_consumed_at_has_id = 0,
  'drop index idx_search_consumed_at on search_consumed_event',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql := if(@has_idx_search_consumed_at = 0 or (@has_idx_search_consumed_at > 0 and @idx_search_consumed_at_has_id = 0),
  'create index idx_search_consumed_at on search_consumed_event(consumed_at, id)',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- ============================================================
-- 结束：建议执行后做一次验证
-- ============================================================
-- 1) 验证索引：
--   show index from outbox_event;
--   show index from consumed_event;
--   show index from search_consumed_event;
--
-- 2) 演练幂等：重复投递同 eventId，确认消费侧不会重复写副作用。
-- 3) 演练 outbox：制造短暂 Kafka 不可用 → 确认恢复后补发正常，FAILED 可按 runbook replay。
