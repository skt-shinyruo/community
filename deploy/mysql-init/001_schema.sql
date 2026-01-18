-- Minimal schema for local docker-compose.
-- Goal: support microservices (auth/user/content/message/search) without pulling external SQL.

create table if not exists user (
  id int auto_increment primary key,
  username varchar(255) not null,
  password varchar(255),
  salt varchar(255),
  email varchar(255),
  type int default 0,
  status int default 0,
  activation_code varchar(255),
  header_url varchar(255),
  create_time timestamp null default current_timestamp
);

create table if not exists discuss_post (
  id int auto_increment primary key,
  user_id int,
  title varchar(255),
  content text,
  type int default 0,
  status int default 0,
  create_time timestamp null default current_timestamp,
  comment_count int default 0,
  score double default 0
);

create table if not exists comment (
  id int auto_increment primary key,
  user_id int,
  entity_type int,
  entity_id int,
  target_id int default 0,
  content text,
  status int default 0,
  create_time timestamp null default current_timestamp
);

create table if not exists message (
  id int auto_increment primary key,
  from_id int,
  to_id int,
  conversation_id varchar(255),
  content varchar(4000),
  status int default 0,
  create_time timestamp null default current_timestamp
);

create table if not exists consumed_event (
  id int auto_increment primary key,
  event_id varchar(64) unique,
  consumed_at timestamp null default current_timestamp
);

create table if not exists search_consumed_event (
  id int auto_increment primary key,
  event_id varchar(64) unique,
  consumed_at timestamp null default current_timestamp
);

create index idx_discuss_post_user_id on discuss_post(user_id);
create index idx_comment_entity on comment(entity_type, entity_id);
create index idx_message_conversation on message(conversation_id);
create index idx_message_to_status on message(to_id, status);
