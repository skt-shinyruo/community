-- Source: 015_schema_growth.sql (market split)
-- --------------------------------------------------------------------
-- Market schema (community): listings, orders, disputes, and fulfillment.

use community;

create table if not exists market_listing (
  listing_id binary(16) primary key,
  seller_user_id binary(16) not null,
  goods_type varchar(16) not null,
  title varchar(128) not null,
  description varchar(1000) not null,
  unit_price bigint not null,
  delivery_mode varchar(16) default null,
  stock_mode varchar(16) default null,
  stock_total int not null,
  stock_available int not null,
  min_purchase_quantity int not null,
  max_purchase_quantity int not null,
  status varchar(16) not null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  key idx_market_listing_seller_time (seller_user_id, create_time)
);

create table if not exists market_inventory_unit (
  inventory_unit_id binary(16) primary key,
  listing_id binary(16) not null,
  seller_user_id binary(16) not null,
  payload_type varchar(16) not null,
  payload_content varchar(4000) not null,
  status varchar(16) not null,
  reserved_order_id binary(16) default null,
  delivered_at timestamp null default null,
  create_time timestamp null default current_timestamp,
  key idx_market_inventory_listing_status (listing_id, status, inventory_unit_id)
);

create table if not exists market_delivery (
  delivery_id binary(16) primary key,
  order_id binary(16) not null,
  seller_user_id binary(16) not null,
  delivery_type varchar(32) not null,
  delivery_content varchar(8000) not null,
  status varchar(16) not null,
  delivered_at timestamp null default null,
  create_time timestamp null default current_timestamp,
  key idx_market_delivery_order (order_id, delivery_id)
);

create table if not exists market_order (
  order_id binary(16) primary key,
  request_id varchar(96) not null,
  listing_id binary(16) not null,
  goods_type varchar(16) not null,
  seller_user_id binary(16) not null,
  buyer_user_id binary(16) not null,
  quantity int not null,
  unit_price_snapshot bigint not null,
  total_amount bigint not null,
  delivery_mode_snapshot varchar(16) default null,
  listing_title_snapshot varchar(128) not null,
  status varchar(16) not null,
  escrow_txn_id binary(16) default null,
  release_txn_id binary(16) default null,
  refund_txn_id binary(16) default null,
  auto_confirm_at timestamp null default null,
  address_id_snapshot binary(16) default null,
  receiver_name_snapshot varchar(64) default null,
  receiver_phone_snapshot varchar(32) default null,
  province_snapshot varchar(64) default null,
  city_snapshot varchar(64) default null,
  district_snapshot varchar(64) default null,
  detail_address_snapshot varchar(255) default null,
  postal_code_snapshot varchar(16) default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_market_order_request (request_id),
  key idx_market_order_buyer_time (buyer_user_id, create_time),
  key idx_market_order_seller_time (seller_user_id, create_time),
  key idx_market_order_listing_status (listing_id, status),
  key idx_market_order_auto_confirm (status, auto_confirm_at)
);

create table if not exists market_wallet_action (
  action_id binary(16) primary key,
  order_id binary(16) not null,
  dispute_id binary(16) default null,
  action_type varchar(16) not null,
  request_id varchar(96) not null,
  wallet_biz_id varchar(96) not null,
  actor_user_id binary(16) not null,
  counterparty_user_id binary(16) default null,
  amount bigint not null,
  status varchar(16) not null,
  result_type varchar(16) default null,
  wallet_txn_id binary(16) default null,
  failure_code varchar(64) default null,
  last_error varchar(255) default null,
  retry_count int not null default 0,
  next_retry_at timestamp null default null,
  processing_lease_until timestamp null default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_market_wallet_action_request (request_id),
  key idx_market_wallet_action_status_next (status, next_retry_at, action_id),
  key idx_market_wallet_action_order_type (order_id, action_type)
);

create table if not exists market_dispute (
  dispute_id binary(16) primary key,
  order_id binary(16) not null,
  goods_type varchar(16) not null,
  buyer_user_id binary(16) not null,
  seller_user_id binary(16) not null,
  status varchar(32) not null,
  reason varchar(255) not null,
  buyer_note varchar(1000) default null,
  seller_note varchar(1000) default null,
  resolution_type varchar(16) default null,
  resolved_by binary(16) default null,
  resolved_at timestamp null default null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  key idx_market_dispute_order_status (order_id, status)
);

create table if not exists market_address (
  address_id binary(16) primary key,
  user_id binary(16) not null,
  receiver_name varchar(64) not null,
  receiver_phone varchar(32) not null,
  province varchar(64) not null,
  city varchar(64) not null,
  district varchar(64) not null,
  detail_address varchar(255) not null,
  postal_code varchar(16) default null,
  is_default boolean not null default false,
  status varchar(16) not null,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  key idx_market_address_user_status (user_id, status, is_default, address_id)
);

create table if not exists market_shipment (
  shipment_id binary(16) primary key,
  order_id binary(16) not null,
  seller_user_id binary(16) not null,
  carrier_name varchar(64) not null,
  tracking_no varchar(128) not null,
  shipping_remark varchar(1000) default null,
  shipped_at timestamp null default current_timestamp,
  create_time timestamp null default current_timestamp,
  update_time timestamp null default current_timestamp on update current_timestamp,
  unique key uk_market_shipment_order (order_id)
);
