alter table oss_upload_session
  add column request_id binary(16) null after session_id,
  add column updated_at timestamp null default null after created_at,
  add column last_error varchar(512) not null default '' after completed_at;

update oss_upload_session
set request_id = session_id,
    updated_at = coalesce(completed_at, created_at)
where request_id is null
   or updated_at is null;

alter table oss_upload_session
  modify column request_id binary(16) not null,
  modify column updated_at timestamp not null,
  add unique key uk_oss_upload_request (request_id),
  add key idx_oss_upload_recovery (status, updated_at, session_id);
