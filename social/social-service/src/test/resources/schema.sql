create table if not exists social_like (
  user_id int not null,
  entity_type int not null,
  entity_id int not null,
  created_at timestamp null default current_timestamp,
  primary key (user_id, entity_type, entity_id)
);

create index if not exists idx_like_entity_user on social_like(entity_type, entity_id, user_id);

create table if not exists social_user_like_count (
  user_id int not null primary key,
  like_count bigint not null default 0,
  updated_at timestamp null default current_timestamp
);

create table if not exists social_follow (
  user_id int not null,
  entity_type int not null,
  entity_id int not null,
  created_at timestamp null default current_timestamp,
  primary key (user_id, entity_type, entity_id)
);

create table if not exists social_block (
  user_id int not null,
  target_user_id int not null,
  created_at timestamp null default current_timestamp,
  primary key (user_id, target_user_id)
);

create table if not exists outbox_event (
  id bigint auto_increment primary key,
  event_id varchar(64) unique,
  topic varchar(128),
  event_key varchar(128),
  payload text,
  status varchar(32),
  retry_count int default 0,
  next_retry_at timestamp,
  last_error varchar(512),
  created_at timestamp null default current_timestamp,
  updated_at timestamp null default current_timestamp
);

create index if not exists idx_outbox_status_next on outbox_event(status, next_retry_at);
create index if not exists idx_outbox_status_updated on outbox_event(status, updated_at, id);
create index if not exists idx_outbox_status_created on outbox_event(status, created_at, id);
