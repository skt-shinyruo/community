create table if not exists user (
  id int primary key,
  username varchar(255)
);

create table if not exists discuss_post (
  id int primary key auto_increment,
  user_id int,
  category_id int,
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

create table if not exists category (
  id int primary key auto_increment,
  name varchar(64),
  description varchar(255),
  position int,
  create_time timestamp
);

create table if not exists tag (
  id int primary key auto_increment,
  name varchar(64),
  create_time timestamp
);

create table if not exists post_tag (
  post_id int,
  tag_id int,
  create_time timestamp,
  primary key (post_id, tag_id)
);

delete from user;
delete from discuss_post;
delete from comment;
delete from post_tag;
delete from tag;
delete from category;

insert into user(id, username) values (1, 'u1');
insert into user(id, username) values (2, 'u2');

insert into category(id, name, description, position, create_time) values (1, '技术', '技术讨论', 10, current_timestamp());
insert into category(id, name, description, position, create_time) values (2, '兴趣', '兴趣分享', 20, current_timestamp());
