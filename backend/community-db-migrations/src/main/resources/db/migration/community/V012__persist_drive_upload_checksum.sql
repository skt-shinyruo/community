alter table drive_upload
  add column checksum_sha256 varchar(128) not null default '';
