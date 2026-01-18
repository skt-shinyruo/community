create table user (
  id int primary key,
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

insert into user(id, username, password, salt, email, type, status, activation_code, header_url, create_time)
values (1, 'u1', 'p', 's', 'u1@example.com', 0, 1, 'ac', 'http://old.local/a.png', CURRENT_TIMESTAMP());

