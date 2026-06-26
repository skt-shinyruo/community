-- Source: 060_schema_notice.sql
-- --------------------------------------------------------------------
-- Notice schema (community): notice_record.

use community;

create table if not exists notice_record (
  id binary(16) primary key,
  sender_user_id binary(16),
  recipient_user_id binary(16) not null,
  topic varchar(64) not null,
  content varchar(4000),
  source_event_type varchar(64),
  source_relation_key varchar(255),
  status int default 0,
  create_time timestamp null default current_timestamp
);

create table if not exists notice_projection_event_log (
  source_event_id varchar(128) not null primary key,
  create_time datetime not null default current_timestamp
);

set @idx_notice_record_topic := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'notice_record'
    and index_name = 'idx_notice_record_topic'
);
set @sql := if(@idx_notice_record_topic = 0, 'create index idx_notice_record_topic on notice_record(topic)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @idx_notice_record_recipient_status := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'notice_record'
    and index_name = 'idx_notice_record_recipient_status'
);
set @sql := if(@idx_notice_record_recipient_status = 0, 'create index idx_notice_record_recipient_status on notice_record(recipient_user_id, status)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @idx_notice_record_recipient_topic_time := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'notice_record'
    and index_name = 'idx_notice_record_recipient_topic_time'
);
set @sql := if(@idx_notice_record_recipient_topic_time = 0, 'create index idx_notice_record_recipient_topic_time on notice_record(recipient_user_id, topic, create_time)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;


-- --------------------------------------------------------------------
