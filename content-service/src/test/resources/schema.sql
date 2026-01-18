create table if not exists user (
  id int primary key,
  username varchar(255)
);

create table if not exists discuss_post (
  id int primary key auto_increment,
  user_id int,
  title varchar(255),
  content text,
  type int,
  status int,
  create_time timestamp,
  comment_count int,
  score double
);

create table if not exists comment (
  id int primary key auto_increment,
  user_id int,
  entity_type int,
  entity_id int,
  target_id int,
  content text,
  status int,
  create_time timestamp
);

delete from user;
delete from discuss_post;
delete from comment;

insert into user(id, username) values (1, 'u1');
insert into user(id, username) values (2, 'u2');

