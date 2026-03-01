create table if not exists discuss_post (
  id int primary key,
  user_id int,
  title varchar(255),
  content varchar(4000),
  type int,
  status int,
  create_time timestamp,
  score double
);

create table if not exists search_consumed_event (
  id int auto_increment primary key,
  event_id varchar(64) unique,
  consumed_at timestamp
);

delete from discuss_post;

insert into discuss_post(id, user_id, title, content, type, status, create_time, score)
values (100, 1, 'hello world', 'hello content', 0, 0, CURRENT_TIMESTAMP, 0.0);
