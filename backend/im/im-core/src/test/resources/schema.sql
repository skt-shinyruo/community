create table if not exists im_room (
  room_id bigint primary key,
  name varchar(128),
  last_seq bigint not null default 0,
  created_at timestamp null default current_timestamp,
  updated_at timestamp null default current_timestamp
);

create table if not exists im_room_member (
  room_id bigint not null,
  user_id int not null,
  role tinyint not null default 0,
  joined_at timestamp null default current_timestamp,
  primary key (room_id, user_id)
);

create table if not exists im_room_message (
  room_id bigint not null,
  seq bigint not null,
  message_id bigint not null,
  from_user_id int not null,
  content text not null,
  client_msg_id varchar(64) not null,
  created_at timestamp null default current_timestamp,
  primary key (room_id, seq),
  unique (room_id, from_user_id, client_msg_id),
  unique (message_id)
);

create table if not exists im_room_read_state (
  room_id bigint not null,
  user_id int not null,
  last_read_seq bigint not null default 0,
  updated_at timestamp null default current_timestamp,
  primary key (room_id, user_id)
);

create table if not exists im_conversation (
  conversation_id varchar(64) primary key,
  user_a int not null,
  user_b int not null,
  last_seq bigint not null default 0,
  created_at timestamp null default current_timestamp,
  updated_at timestamp null default current_timestamp
);

create table if not exists im_private_message (
  conversation_id varchar(64) not null,
  seq bigint not null,
  message_id bigint not null,
  from_user_id int not null,
  to_user_id int not null,
  content text not null,
  client_msg_id varchar(64) not null,
  created_at timestamp null default current_timestamp,
  primary key (conversation_id, seq),
  unique (conversation_id, from_user_id, client_msg_id),
  unique (message_id)
);

create table if not exists im_conversation_read_state (
  conversation_id varchar(64) not null,
  user_id int not null,
  last_read_seq bigint not null default 0,
  updated_at timestamp null default current_timestamp,
  primary key (conversation_id, user_id)
);

