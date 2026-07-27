alter table drive_upload
  modify column object_id binary(16) null,
  modify column version_id binary(16) null,
  modify column oss_session_id binary(16) null;
