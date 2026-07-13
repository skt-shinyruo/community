-- Source: 060_schema_notice.sql
-- --------------------------------------------------------------------
-- Notice schema (community): notice_record.

use community;

create table if not exists notice_record (
  id binary(16) primary key,
  sender_user_id binary(16),
  recipient_user_id binary(16) not null,
  topic varchar(64) not null,
  content varchar(4000),
  source_event_type varchar(64),
  source_relation_key varchar(255),
  status int default 0,
  create_time timestamp null default current_timestamp,
  index idx_notice_record_topic (topic),
  index idx_notice_record_recipient_status (recipient_user_id, status),
  index idx_notice_record_recipient_topic_time (recipient_user_id, topic, create_time)
);

create table if not exists notice_projection_event_log (
  source_event_id varchar(128) not null primary key,
  create_time datetime not null default current_timestamp
);


-- --------------------------------------------------------------------
