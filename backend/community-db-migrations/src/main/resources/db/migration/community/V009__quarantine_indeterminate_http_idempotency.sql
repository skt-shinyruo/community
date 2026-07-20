update http_idempotency
set status = 'I',
    processing_expires_at = null,
    updated_at = current_timestamp
where status = 'P';
