alter table product_image add column is_primary boolean not null default false;
alter table product_image add column sort_order integer not null default 0;
create index idx_product_image_display on product_image(product_id, is_primary, sort_order);
