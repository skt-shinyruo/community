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

create table if not exists http_idempotency (
  id bigint auto_increment primary key,
  operation varchar(64) not null,
  user_id int not null,
  idem_key varchar(128) not null,
  status varchar(16) not null,
  response_json mediumtext,
  processing_expires_at timestamp,
  success_expires_at timestamp,
  created_at timestamp default current_timestamp,
  updated_at timestamp default current_timestamp,
  unique (operation, user_id, idem_key)
);

delete from http_idempotency;
