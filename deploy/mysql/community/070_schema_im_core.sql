-- Source: 031_schema_im_core.sql
-- --------------------------------------------------------------------
-- IM Core schema (im_core): room + private message (seq-based) + read watermarks.

use im_core;

create table if not exists outbox_event (
  id binary(16) primary key,
  event_id varchar(64) not null,
  topic varchar(255) not null,
  event_key varchar(255) not null,
  payload mediumtext not null,
  status varchar(32) not null,
  retry_count int not null default 0,
  next_retry_at timestamp null default null,
  last_error varchar(255),
  trace_id varchar(32) null,
  traceparent varchar(128) null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  unique key uk_outbox_event_id (event_id),
  index idx_outbox_status_next (status, next_retry_at, id)
);

set @col_outbox_trace_id := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'outbox_event'
    and column_name = 'trace_id'
);
set @sql := if(@col_outbox_trace_id = 0, 'alter table outbox_event add column trace_id varchar(32) null after last_error', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_outbox_traceparent := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'outbox_event'
    and column_name = 'traceparent'
);
set @sql := if(@col_outbox_traceparent = 0, 'alter table outbox_event add column traceparent varchar(128) null after trace_id', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @idx_im_core_outbox_status_updated := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'outbox_event'
    and index_name = 'idx_outbox_status_updated'
);
set @sql := if(@idx_im_core_outbox_status_updated = 0, 'create index idx_outbox_status_updated on outbox_event(status, updated_at, id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @idx_im_core_outbox_status_created := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'outbox_event'
    and index_name = 'idx_outbox_status_created'
);
set @sql := if(@idx_im_core_outbox_status_created = 0, 'create index idx_outbox_status_created on outbox_event(status, created_at, id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

create table if not exists im_room (
  room_id binary(16) primary key,
  name varchar(128),
  last_seq bigint not null default 0,
  created_at timestamp null default current_timestamp,
  updated_at timestamp null default current_timestamp on update current_timestamp
);

create table if not exists im_room_member (
  room_id binary(16) not null,
  user_id binary(16) not null,
  role tinyint not null default 0,
  joined_at timestamp null default current_timestamp,
  primary key (room_id, user_id)
);

set @idx_im_room_member_user := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'im_room_member'
    and index_name = 'idx_im_room_member_user'
);
set @sql := if(@idx_im_room_member_user = 0, 'create index idx_im_room_member_user on im_room_member(user_id, room_id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

create table if not exists im_room_message (
  room_id binary(16) not null,
  seq bigint not null,
  message_id binary(16) not null,
  from_user_id binary(16) not null,
  content mediumtext not null,
  client_msg_id varchar(64) not null,
  created_at timestamp null default current_timestamp,
  primary key (room_id, seq),
  unique key uk_im_room_message_idempotency (room_id, from_user_id, client_msg_id),
  unique key uk_im_room_message_id (message_id)
);

set @idx_im_room_message_created_at := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'im_room_message'
    and index_name = 'idx_im_room_message_created_at'
);
set @sql := if(@idx_im_room_message_created_at = 0, 'create index idx_im_room_message_created_at on im_room_message(room_id, created_at, seq)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

create table if not exists im_room_read_state (
  room_id binary(16) not null,
  user_id binary(16) not null,
  last_read_seq bigint not null default 0,
  updated_at timestamp null default current_timestamp on update current_timestamp,
  primary key (room_id, user_id)
);

set @idx_im_room_read_state_user := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'im_room_read_state'
    and index_name = 'idx_im_room_read_state_user'
);
set @sql := if(@idx_im_room_read_state_user = 0, 'create index idx_im_room_read_state_user on im_room_read_state(user_id, room_id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

create table if not exists im_conversation (
  conversation_id varchar(80) primary key,
  user_a binary(16) not null,
  user_b binary(16) not null,
  last_seq bigint not null default 0,
  created_at timestamp null default current_timestamp,
  updated_at timestamp null default current_timestamp on update current_timestamp
);

set @idx_im_conversation_users := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'im_conversation'
    and index_name = 'idx_im_conversation_users'
);
set @sql := if(@idx_im_conversation_users = 0, 'create index idx_im_conversation_users on im_conversation(user_a, user_b, conversation_id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

create table if not exists im_private_message (
  conversation_id varchar(80) not null,
  seq bigint not null,
  message_id binary(16) not null,
  from_user_id binary(16) not null,
  to_user_id binary(16) not null,
  content mediumtext not null,
  client_msg_id varchar(64) not null,
  created_at timestamp null default current_timestamp,
  primary key (conversation_id, seq),
  unique key uk_im_private_message_idempotency (conversation_id, from_user_id, client_msg_id),
  unique key uk_im_private_message_id (message_id)
);

set @idx_im_private_message_to := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'im_private_message'
    and index_name = 'idx_im_private_message_to'
);
set @sql := if(@idx_im_private_message_to = 0, 'create index idx_im_private_message_to on im_private_message(to_user_id, conversation_id, seq)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

create table if not exists im_conversation_read_state (
  conversation_id varchar(80) not null,
  user_id binary(16) not null,
  last_read_seq bigint not null default 0,
  updated_at timestamp null default current_timestamp on update current_timestamp,
  primary key (conversation_id, user_id)
);

set @idx_im_conversation_read_state_user := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'im_conversation_read_state'
    and index_name = 'idx_im_conversation_read_state_user'
);
set @sql := if(@idx_im_conversation_read_state_user = 0, 'create index idx_im_conversation_read_state_user on im_conversation_read_state(user_id, conversation_id)', 'select 1');
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;



-- --------------------------------------------------------------------
