create table if not exists im_room (
  room_id binary(16) primary key,
  name varchar(128),
  last_seq bigint not null default 0,
  created_at timestamp null default current_timestamp,
  updated_at timestamp null default current_timestamp
);

create table if not exists im_room_member (
  room_id binary(16) not null,
  user_id binary(16) not null,
  role tinyint not null default 0,
  joined_at timestamp null default current_timestamp,
  primary key (room_id, user_id)
);

create table if not exists im_room_message (
  room_id binary(16) not null,
  seq bigint not null,
  message_id binary(16) not null,
  from_user_id binary(16) not null,
  content text not null,
  client_msg_id varchar(64) not null,
  created_at timestamp null default current_timestamp,
  primary key (room_id, seq),
  unique (room_id, from_user_id, client_msg_id),
  unique (message_id)
);

create table if not exists im_room_read_state (
  room_id binary(16) not null,
  user_id binary(16) not null,
  last_read_seq bigint not null default 0,
  updated_at timestamp null default current_timestamp,
  primary key (room_id, user_id)
);

create table if not exists im_conversation (
  conversation_id varchar(80) primary key,
  user_a binary(16) not null,
  user_b binary(16) not null,
  last_seq bigint not null default 0,
  created_at timestamp null default current_timestamp,
  updated_at timestamp null default current_timestamp
);

create table if not exists im_private_message (
  conversation_id varchar(80) not null,
  seq bigint not null,
  message_id binary(16) not null,
  from_user_id binary(16) not null,
  to_user_id binary(16) not null,
  content text not null,
  client_msg_id varchar(64) not null,
  created_at timestamp null default current_timestamp,
  primary key (conversation_id, seq),
  unique (conversation_id, from_user_id, client_msg_id),
  unique (message_id)
);

create table if not exists im_conversation_read_state (
  conversation_id varchar(80) not null,
  user_id binary(16) not null,
  last_read_seq bigint not null default 0,
  updated_at timestamp null default current_timestamp,
  primary key (conversation_id, user_id)
);

create table if not exists outbox_event (
  id binary(16) primary key,
  event_id varchar(64) not null,
  topic varchar(255) not null,
  event_key varchar(255) not null,
  payload clob not null,
  status varchar(32) not null,
  retry_count int not null default 0,
  next_retry_at timestamp,
  last_error varchar(512),
  trace_id varchar(32) null,
  traceparent varchar(128) null,
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  constraint uk_outbox_event_id unique (event_id)
);

create index if not exists idx_outbox_status_next on outbox_event(status, next_retry_at, id);
create index if not exists idx_outbox_status_updated on outbox_event(status, updated_at, id);
create index if not exists idx_outbox_status_created on outbox_event(status, created_at, id);
