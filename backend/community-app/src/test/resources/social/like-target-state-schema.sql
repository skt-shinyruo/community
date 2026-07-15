create table if not exists social_like_target_state (
  entity_type int not null,
  entity_id binary(16) not null,
  status varchar(16) not null,
  source_event_id varchar(128),
  source_version bigint not null default 0,
  deleted_at timestamp(3),
  updated_at timestamp(3) not null default current_timestamp,
  primary key (entity_type, entity_id),
  constraint ck_social_like_target_state_status check (status in ('ACTIVE', 'DELETED'))
);

create index if not exists idx_like_target_state_status_updated
  on social_like_target_state(status, updated_at, entity_type, entity_id);
