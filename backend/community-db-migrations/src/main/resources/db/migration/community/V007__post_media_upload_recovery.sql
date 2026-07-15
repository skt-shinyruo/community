alter table post_media_asset
  add column upload_status varchar(32) not null default 'PREPARED' after lifecycle,
  add column upload_operation_version bigint not null default 0 after upload_status,
  add column upload_updated_at timestamp null default null after upload_operation_version,
  add key idx_post_media_upload_recovery (upload_status, upload_updated_at, id),
  add constraint ck_post_media_upload_status check (
    upload_status in ('PREPARED', 'COMPLETING', 'OBJECT_COMPLETED', 'COMPLETED', 'FAILED')
  );

update post_media_asset
set upload_status = case
      when lifecycle = 'DRAFT' then 'PREPARED'
      else 'COMPLETED'
    end,
    upload_operation_version = case
      when lifecycle = 'DRAFT' then 0
      else 1
    end,
    upload_updated_at = coalesce(update_time, create_time, current_timestamp);
