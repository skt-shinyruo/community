create table if not exists user (
  id int auto_increment primary key,
  username varchar(255),
  password varchar(255),
  salt varchar(255),
  email varchar(255),
  type int,
  status int,
  activation_code varchar(255),
  header_url varchar(255),
  create_time timestamp
);

delete from user;

insert into user (id, username, password, salt, email, type, status, activation_code, header_url, create_time)
values (1, 'aaa', 'be5cdc88ad25c5aa86b9a9e1c3573e79', 'salt', 'aaa@example.com', 0, 1, 'ac', 'http://example.com/a.png', CURRENT_TIMESTAMP);
