alter table payment_order add column expires_at timestamp with time zone;

update payment_order
set expires_at = created_at
where expires_at is null;

alter table payment_order alter column expires_at set not null;

create index idx_payment_order_timeout
    on payment_order(status, expires_at);
