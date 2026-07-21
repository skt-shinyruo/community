alter table market_wallet_action
  add column lease_token binary(16) null after processing_lease_until,
  add index idx_market_wallet_action_processing_lease(status, processing_lease_until, action_id);

update market_wallet_action
set status = 'RETRYING',
    retry_count = retry_count + 1,
    next_retry_at = current_timestamp,
    processing_lease_until = null,
    lease_token = null,
    update_time = current_timestamp
where status = 'PROCESSING';
