use community_oss;

create table if not exists oss_object (
  object_id binary(16) primary key,
  usage varchar(64) not null,
  owner_service varchar(64) not null,
  owner_domain varchar(64) not null,
  owner_type varchar(64) not null,
  owner_id varchar(128) not null,
  visibility varchar(32) not null,
  status varchar(32) not null,
  current_version_id binary(16) null,
  latest_file_name varchar(255) not null default '',
  latest_content_type varchar(128) not null default 'application/octet-stream',
  latest_content_length bigint not null default 0,
  latest_checksum_sha256 varchar(128) not null default '',
  retention_until timestamp null default null,
  delete_after timestamp null default null,
  legal_hold_until timestamp null default null,
  created_by varchar(128) not null default '',
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  key idx_oss_object_owner (owner_service, owner_domain, owner_type, owner_id),
  key idx_oss_object_status (status, updated_at),
  key idx_oss_object_current_version (current_version_id)
);

create table if not exists oss_object_version (
  version_id binary(16) primary key,
  object_id binary(16) not null,
  version_no int not null,
  storage_backend varchar(64) not null,
  storage_bucket varchar(128) not null,
  storage_key varchar(1024) not null,
  status varchar(32) not null,
  file_name varchar(255) not null,
  content_type varchar(128) not null default 'application/octet-stream',
  content_length bigint not null default 0,
  checksum_sha256 varchar(128) not null default '',
  etag varchar(255) not null default '',
  cache_control varchar(255) not null default '',
  content_disposition varchar(255) not null default '',
  source_object_id binary(16) null,
  variant_type varchar(64) not null default '',
  created_at timestamp not null default current_timestamp,
  activated_at timestamp null default null,
  expired_at timestamp null default null,
  purged_at timestamp null default null,
  unique key uk_oss_object_version_no (object_id, version_no),
  key idx_oss_object_version_object_status (object_id, status),
  key idx_oss_object_version_source (source_object_id, variant_type)
);

create table if not exists oss_upload_session (
  session_id binary(16) primary key,
  object_id binary(16) not null,
  version_id binary(16) not null,
  upload_mode varchar(32) not null,
  owner_service varchar(64) not null,
  owner_domain varchar(64) not null,
  owner_type varchar(64) not null,
  owner_id varchar(128) not null,
  expected_file_name varchar(255) not null,
  expected_content_type varchar(128) not null default 'application/octet-stream',
  expected_content_length bigint not null default 0,
  expected_checksum_sha256 varchar(128) not null default '',
  alias_key varchar(512) not null default '',
  status varchar(32) not null,
  expires_at timestamp not null,
  created_by varchar(128) not null default '',
  created_at timestamp not null default current_timestamp,
  completed_at timestamp null default null,
  key idx_oss_upload_object (object_id, version_id),
  key idx_oss_upload_status_expiry (status, expires_at)
);

create table if not exists oss_access_grant (
  grant_id binary(16) primary key,
  object_id binary(16) not null,
  version_id binary(16) null,
  principal_type varchar(32) not null,
  principal_value varchar(128) not null,
  permission varchar(32) not null,
  expires_at timestamp null default null,
  created_by varchar(128) not null default '',
  created_at timestamp not null default current_timestamp,
  revoked_at timestamp null default null,
  key idx_oss_access_object (object_id, version_id),
  key idx_oss_access_principal (principal_type, principal_value, permission)
);

create table if not exists oss_object_reference (
  reference_id binary(16) primary key,
  object_id binary(16) not null,
  version_id binary(16) null,
  subject_service varchar(64) not null,
  subject_domain varchar(64) not null,
  subject_type varchar(64) not null,
  subject_id varchar(128) not null,
  reference_role varchar(64) not null,
  status varchar(32) not null,
  retain_until timestamp null default null,
  created_at timestamp not null default current_timestamp,
  released_at timestamp null default null,
  key idx_oss_reference_object (object_id, version_id, status),
  key idx_oss_reference_subject (subject_service, subject_domain, subject_type, subject_id)
);

create table if not exists oss_usage_policy (
  usage varchar(64) primary key,
  default_visibility varchar(32) not null,
  max_bytes bigint not null,
  allowed_mime_types varchar(1024) not null default '',
  requires_checksum tinyint(1) not null default 0,
  requires_scan tinyint(1) not null default 0,
  versioning_enabled tinyint(1) not null default 1,
  download_ttl_seconds bigint not null default 300,
  upload_ttl_seconds bigint not null default 900,
  public_cache_control varchar(255) not null default '',
  private_cache_control varchar(255) not null default 'no-store',
  retention_days int not null default 0,
  delete_grace_days int not null default 7
);

create table if not exists oss_object_alias (
  alias_key varchar(512) primary key,
  object_id binary(16) not null,
  version_id binary(16) not null,
  status varchar(32) not null,
  expires_at timestamp null default null,
  created_at timestamp not null default current_timestamp,
  key idx_oss_alias_object (object_id, version_id),
  key idx_oss_alias_status_expiry (status, expires_at)
);

insert into oss_usage_policy (
  usage, default_visibility, max_bytes, allowed_mime_types, requires_checksum, requires_scan,
  versioning_enabled, download_ttl_seconds, upload_ttl_seconds, public_cache_control,
  private_cache_control, retention_days, delete_grace_days
) values (
  'DRIVE_FILE', 'PRIVATE', 10737418240, '', 0, 0,
  1, 300, 900, '', 'no-store', 0, 7
) on duplicate key update
  default_visibility = values(default_visibility),
  max_bytes = values(max_bytes),
  download_ttl_seconds = values(download_ttl_seconds),
  upload_ttl_seconds = values(upload_ttl_seconds),
  private_cache_control = values(private_cache_control);
