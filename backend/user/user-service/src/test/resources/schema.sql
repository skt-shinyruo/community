create table if not exists user (
  id int primary key,
  username varchar(255),
  password varchar(255),
  salt varchar(255),
  email varchar(255),
  type int,
  status int,
  activation_code varchar(255),
  header_url varchar(255),
  create_time timestamp,
  score int default 0,
  mute_until timestamp,
  ban_until timestamp
);

create table if not exists user_score_log (
  id bigint auto_increment primary key,
  user_id int not null,
  event_id varchar(64) not null,
  event_type varchar(64) not null,
  delta int not null,
  create_time timestamp default current_timestamp,
  constraint uk_event_id unique (event_id)
);

create table if not exists user_consumed_event (
  id bigint auto_increment primary key,
  event_id varchar(64) not null,
  consumed_at timestamp default current_timestamp,
  constraint uk_user_consumed_event_id unique (event_id)
);

create table if not exists auth_refresh_token (
  token_hash char(64) primary key,
  user_id int not null,
  family_id varchar(64) not null,
  expires_at timestamp not null,
  revoked_at timestamp,
  created_at timestamp default current_timestamp
);

create table if not exists outbox_event (
  id bigint auto_increment primary key,
  event_id varchar(64) not null,
  topic varchar(255) not null,
  event_key varchar(255) not null,
  payload clob not null,
  status varchar(32) not null,
  retry_count int not null default 0,
  next_retry_at timestamp,
  last_error varchar(255),
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  constraint uk_outbox_event_id unique (event_id)
);

create index if not exists idx_outbox_status_next on outbox_event(status, next_retry_at, id);
create index if not exists idx_outbox_status_updated on outbox_event(status, updated_at, id);
create index if not exists idx_outbox_status_created on outbox_event(status, created_at, id);

merge into user (id, username, password, salt, email, type, status, activation_code, header_url, create_time, score) key(id) values (1, 'u1', 'p', 's', 'u1@example.com', 0, 1, 'ac', 'http://old.local/a.png', CURRENT_TIMESTAMP(), 0);
