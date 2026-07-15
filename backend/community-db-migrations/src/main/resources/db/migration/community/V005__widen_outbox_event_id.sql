alter table outbox_event
  modify column event_id varchar(128) not null;
