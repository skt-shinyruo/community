create table if not exists message (
  id int auto_increment primary key,
  from_id int,
  to_id int,
  conversation_id varchar(255),
  content varchar(4000),
  status int,
  create_time timestamp
);

create table if not exists consumed_event (
  id int auto_increment primary key,
  event_id varchar(64) unique,
  consumed_at timestamp
);

create table if not exists user_moderation_projection (
  user_id int primary key,
  mute_until timestamp,
  ban_until timestamp,
  updated_at timestamp default current_timestamp
);

create table if not exists user_block_projection (
  blocker_user_id int not null,
  blocked_user_id int not null,
  blocked int default 1,
  updated_at timestamp default current_timestamp,
  primary key (blocker_user_id, blocked_user_id)
);

delete from user_moderation_projection;
insert into user_moderation_projection(user_id, mute_until, ban_until, updated_at) values (1, null, null, current_timestamp());
