alter table post_media_asset
  add column reference_status varchar(32) not null default 'UNBOUND' after lifecycle,
  add column reference_operation_version bigint not null default 0 after reference_status,
  add column reference_updated_at timestamp null default null after reference_operation_version,
  add key idx_post_media_reference_pending (reference_status, reference_updated_at),
  add constraint ck_post_media_reference_status check (
    reference_status in ('UNBOUND', 'BIND_PENDING', 'BOUND', 'RELEASE_PENDING', 'RELEASED')
  );

update post_media_asset
set reference_status = case
      when lifecycle = 'RELEASED' and oss_reference_id is not null then 'RELEASE_PENDING'
      when lifecycle = 'RELEASED' then 'RELEASED'
      when oss_reference_id is not null then 'BOUND'
      when post_id is not null then 'BIND_PENDING'
      else 'UNBOUND'
    end,
    reference_operation_version = case
      when post_id is not null or oss_reference_id is not null or lifecycle = 'RELEASED' then 1
      else 0
    end,
    reference_updated_at = coalesce(update_time, create_time, current_timestamp);
