use community;

create table if not exists drive_space (
  space_id binary(16) primary key,
  user_id binary(16) not null,
  quota_bytes bigint not null default 10737418240,
  used_bytes bigint not null default 0,
  reserved_bytes bigint not null default 0,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  unique key uk_drive_space_user (user_id),
  key idx_drive_space_updated (updated_at)
);

create table if not exists drive_entry (
  entry_id binary(16) primary key,
  space_id binary(16) not null,
  parent_id binary(16) null,
  parent_key varchar(32) not null default '',
  active_name varchar(255) null,
  type varchar(16) not null,
  name varchar(255) not null,
  object_id binary(16) null,
  version_id binary(16) null,
  size_bytes bigint not null default 0,
  mime_type varchar(128) not null default '',
  status varchar(16) not null,
  trashed_at timestamp null default null,
  delete_after timestamp null default null,
  trash_root_id binary(16) null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  unique key uk_drive_entry_active_name (space_id, parent_key, active_name),
  key idx_drive_entry_parent_status (space_id, parent_id, status, name),
  key idx_drive_entry_object (object_id, version_id),
  key idx_drive_entry_trash (space_id, status, trashed_at),
  key idx_drive_entry_search (space_id, status, name)
);

create table if not exists drive_upload (
  upload_id binary(16) primary key,
  space_id binary(16) not null,
  parent_id binary(16) null,
  name varchar(255) not null,
  size_bytes bigint not null,
  mime_type varchar(128) not null,
  object_id binary(16) not null,
  version_id binary(16) not null,
  oss_session_id binary(16) not null,
  status varchar(16) not null,
  created_by binary(16) not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  expires_at timestamp not null,
  completed_at timestamp null default null,
  completed_entry_id binary(16) null,
  key idx_drive_upload_space_status (space_id, status, expires_at),
  key idx_drive_upload_recovery (status, updated_at, upload_id),
  key idx_drive_upload_object (object_id, version_id)
);

create table if not exists drive_share (
  share_id binary(16) primary key,
  entry_id binary(16) not null,
  share_token varchar(96) not null,
  password_hash varchar(255) not null,
  expires_at timestamp not null,
  status varchar(16) not null,
  created_by binary(16) not null,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  unique key uk_drive_share_token (share_token),
  key idx_drive_share_entry_status (entry_id, status),
  key idx_drive_share_expiry (status, expires_at)
);

create table if not exists drive_share_access (
  access_id binary(16) primary key,
  share_id binary(16) not null,
  visitor_fingerprint varchar(128) not null default '',
  success tinyint(1) not null default 0,
  accessed_at timestamp not null default current_timestamp,
  key idx_drive_share_access_share_time (share_id, accessed_at),
  key idx_drive_share_access_fingerprint_time (visitor_fingerprint, accessed_at)
);
