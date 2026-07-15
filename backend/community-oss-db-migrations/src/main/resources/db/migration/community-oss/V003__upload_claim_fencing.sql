alter table oss_upload_session
  add column claim_version bigint not null default 0 after status;
