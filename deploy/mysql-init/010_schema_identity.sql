-- Identity schema (keep in MYSQL_DATABASE=community for P0).
-- NOTE: This schema is still shared by auth-service and user-service in P0.

use community;

create table if not exists user (
  id int auto_increment primary key,
  username varchar(255) not null,
  password varchar(255),
  salt varchar(255),
  email varchar(255),
  type int default 0,
  status int default 0,
  activation_code varchar(255),
  header_url varchar(255),
  create_time timestamp null default current_timestamp
);

