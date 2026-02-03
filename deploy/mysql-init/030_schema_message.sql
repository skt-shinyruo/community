-- Message schema (community_message): message + consumed_event.

use community_message;

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

create index idx_consumed_event_at on consumed_event(consumed_at, id);

create index idx_message_conversation on message(conversation_id);
create index idx_message_to_status on message(to_id, status);

-- message-service 本地投影（最终一致）：处罚状态与拉黑关系（用于私信写路径拦截）
create table if not exists user_moderation_projection (
  user_id int primary key,
  mute_until timestamp null default null,
  ban_until timestamp null default null,
  updated_at timestamp null default current_timestamp
);

create table if not exists user_block_projection (
  blocker_user_id int not null,
  blocked_user_id int not null,
  blocked tinyint not null default 1,
  updated_at timestamp null default current_timestamp,
  primary key (blocker_user_id, blocked_user_id)
);
