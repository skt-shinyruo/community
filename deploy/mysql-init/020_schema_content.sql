-- Content schema (community_content): posts + comments.

use community_content;

create table if not exists discuss_post (
  id int auto_increment primary key,
  user_id int,
  title varchar(255),
  content text,
  type int default 0,
  status int default 0,
  create_time timestamp null default current_timestamp,
  comment_count int default 0,
  score double default 0
);

create table if not exists comment (
  id int auto_increment primary key,
  user_id int,
  entity_type int,
  entity_id int,
  target_id int default 0,
  content text,
  status int default 0,
  create_time timestamp null default current_timestamp
);

create index idx_discuss_post_user_id on discuss_post(user_id);
create index idx_comment_entity on comment(entity_type, entity_id);

