-- Source: 030_schema_message.sql
-- --------------------------------------------------------------------
-- Message schema (community): message.

use community;

create table if not exists message (
  id int auto_increment primary key,
  from_id int,
  to_id int,
  conversation_id varchar(255),
  content varchar(4000),
  status int default 0,
  create_time timestamp null default current_timestamp
);

set @idx_message_conversation := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'message'
    and index_name = 'idx_message_conversation'
);
set @sql := if(@idx_message_conversation = 0, 'create index idx_message_conversation on message(conversation_id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @idx_message_to_status := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'message'
    and index_name = 'idx_message_to_status'
);
set @sql := if(@idx_message_to_status = 0, 'create index idx_message_to_status on message(to_id, status)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;


-- --------------------------------------------------------------------
