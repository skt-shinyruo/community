-- Search schema (community_search): search service idempotency table.

use community_search;

create table if not exists search_consumed_event (
  id int auto_increment primary key,
  event_id varchar(64) unique,
  consumed_at timestamp null default current_timestamp
);

create index idx_search_consumed_at on search_consumed_event(consumed_at);
