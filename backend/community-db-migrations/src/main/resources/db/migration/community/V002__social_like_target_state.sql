create table social_like_target_state (
  entity_type int not null,
  entity_id binary(16) not null,
  status varchar(16) not null default 'ACTIVE',
  source_event_id varchar(128) null,
  source_version bigint not null default 0,
  deleted_at timestamp null default null,
  updated_at timestamp not null default current_timestamp on update current_timestamp,
  primary key (entity_type, entity_id),
  key idx_social_like_target_state_status_updated (status, updated_at),
  constraint ck_social_like_target_state_status check (status in ('ACTIVE', 'DELETED'))
);
