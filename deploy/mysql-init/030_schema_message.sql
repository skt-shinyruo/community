-- Message schema (community): message + consumed_event.

use community;

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
