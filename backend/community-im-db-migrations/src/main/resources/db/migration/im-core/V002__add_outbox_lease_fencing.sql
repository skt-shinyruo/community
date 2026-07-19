alter table outbox_event
  add column lease_token binary(16) null after status,
  add column processing_lease_until timestamp null after lease_token,
  add index idx_outbox_processing_lease(status, processing_lease_until, id);
