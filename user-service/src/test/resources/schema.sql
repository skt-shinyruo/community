create table if not exists user (
  id int primary key,
  username varchar(255),
  password varchar(255),
  salt varchar(255),
  email varchar(255),
  type int,
  status int,
  activation_code varchar(255),
  header_url varchar(255),
  create_time timestamp,
  score int default 0,
  mute_until timestamp,
  ban_until timestamp
);

create table if not exists user_score_log (
  id bigint auto_increment primary key,
  user_id int not null,
  event_id varchar(64) not null,
  event_type varchar(64) not null,
  delta int not null,
  create_time timestamp default current_timestamp,
  constraint uk_event_id unique (event_id)
);

merge into user (id, username, password, salt, email, type, status, activation_code, header_url, create_time, score) key(id) values (1, 'u1', 'p', 's', 'u1@example.com', 0, 1, 'ac', 'http://old.local/a.png', CURRENT_TIMESTAMP(), 0);
