-- Frozen from deploy/mysql/community/070_schema_im_core.sql.
-- This migration is database agnostic; the JDBC URL selects the IM-owned database.

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
  index idx_outbox_status_next (status, next_retry_at, id),
  index idx_outbox_status_updated (status, updated_at, id),
  index idx_outbox_status_created (status, created_at, id)
);

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
  version bigint not null default 0,
  primary key (room_id, user_id),
  index idx_im_room_member_user (user_id, room_id)
);

create table if not exists im_membership_version_counter (
  id int primary key,
  current_version bigint not null default 0
);

set @im_membership_current_version := (
  select coalesce(max(version), 0) from im_room_member
);

insert into im_membership_version_counter(id, current_version)
values (1, @im_membership_current_version)
on duplicate key update current_version = greatest(current_version, values(current_version));

create table if not exists im_membership_version_log (
  version bigint primary key,
  room_id binary(16) not null,
  user_id binary(16) not null,
  active tinyint(1) not null,
  occurred_at timestamp not null default current_timestamp,
  index idx_im_membership_version_pair (room_id, user_id, version)
);

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
  unique key uk_im_room_message_id (message_id),
  index idx_im_room_message_created_at (room_id, created_at, seq)
);

create table if not exists im_room_read_state (
  room_id binary(16) not null,
  user_id binary(16) not null,
  last_read_seq bigint not null default 0,
  updated_at timestamp null default current_timestamp on update current_timestamp,
  primary key (room_id, user_id),
  index idx_im_room_read_state_user (user_id, room_id)
);

create table if not exists im_conversation (
  conversation_id varchar(80) primary key,
  user_a binary(16) not null,
  user_b binary(16) not null,
  last_seq bigint not null default 0,
  created_at timestamp null default current_timestamp,
  updated_at timestamp null default current_timestamp on update current_timestamp,
  index idx_im_conversation_users (user_a, user_b, conversation_id)
);

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
  unique key uk_im_private_message_id (message_id),
  index idx_im_private_message_to (to_user_id, conversation_id, seq)
);

create table if not exists im_conversation_read_state (
  conversation_id varchar(80) not null,
  user_id binary(16) not null,
  last_read_seq bigint not null default 0,
  updated_at timestamp null default current_timestamp on update current_timestamp,
  primary key (conversation_id, user_id),
  index idx_im_conversation_read_state_user (user_id, conversation_id)
);

create table if not exists im_user_conversation_inbox (
  user_id binary(16) not null,
  conversation_id varchar(80) not null,
  peer_user_id binary(16) not null,
  last_seq bigint not null default 0,
  last_message_id binary(16),
  last_from_user_id binary(16),
  last_to_user_id binary(16),
  last_content mediumtext,
  last_message_created_at timestamp null default null,
  last_read_seq bigint not null default 0,
  unread_count bigint not null default 0,
  sort_at timestamp null default current_timestamp,
  created_at timestamp null default current_timestamp,
  updated_at timestamp null default current_timestamp on update current_timestamp,
  primary key (user_id, conversation_id),
  index idx_im_user_conversation_inbox_user_sort (user_id, sort_at, conversation_id)
);

create table if not exists im_user_room_inbox (
  user_id binary(16) not null,
  room_id binary(16) not null,
  last_seq bigint not null default 0,
  last_message_id binary(16),
  last_from_user_id binary(16),
  last_content mediumtext,
  last_message_created_at timestamp null default null,
  last_read_seq bigint not null default 0,
  unread_count bigint not null default 0,
  sort_at timestamp null default current_timestamp,
  created_at timestamp null default current_timestamp,
  updated_at timestamp null default current_timestamp on update current_timestamp,
  primary key (user_id, room_id),
  index idx_im_user_room_inbox_user_sort (user_id, sort_at, room_id)
);
