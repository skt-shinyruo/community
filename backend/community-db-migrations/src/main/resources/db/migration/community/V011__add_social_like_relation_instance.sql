alter table social_like
  add column relation_instance_id binary(16) null;

update social_like
set relation_instance_id = uuid_to_bin(uuid());

alter table social_like
  add unique key uk_social_like_relation_instance(relation_instance_id);

alter table social_like
  modify column relation_instance_id binary(16) not null;
