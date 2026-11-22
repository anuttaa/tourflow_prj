create table destinations (
    id bigserial primary key,
    name varchar(120) not null,
    country varchar(80) not null,
    tz varchar(60) not null,
    lat double precision,
    lon double precision,
    unique (name, country)
);
create table suppliers (
    id bigserial primary key,
    name varchar(150) not null unique,
    contact varchar(200),
    active boolean not null default true
);
create table offers (
    id bigserial primary key,
    supplier_id bigint not null references suppliers(id),
    destination_id bigint not null references destinations(id),
    type varchar(20) not null check (type in ('FLIGHT','TRAIN','BUS','HOTEL','TRANSFER','EXCURSION','INSURANCE','MEAL')),
    title varchar(200) not null,
    description varchar(2000),
    class_level int not null default 3 check (class_level between 1 and 5),
    base_price numeric(12,2) not null check (base_price >= 0),
    capacity int not null check (capacity > 0),
    start_utc timestamptz,
    end_utc timestamptz,
    tz varchar(60) not null,
    place_from varchar(200),
    place_to varchar(200),
    from_lat double precision, from_lon double precision,
    to_lat double precision, to_lon double precision,
    international boolean not null default false,
    check_in_from int check (check_in_from is null or check_in_from between 0 and 1439),
    check_out_until int check (check_out_until is null or check_out_until between 0 and 1439),
    attrs jsonb not null default '{}'::jsonb,
    rating_avg numeric(3,2) not null default 0,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (start_utc is null or end_utc is null or end_utc > start_utc)
);
create index idx_offers_dest_type on offers (destination_id, type) where active;
create index idx_offers_range on offers using gist (tstzrange(start_utc, end_utc));
create index idx_offers_attrs on offers using gin (attrs);
create table offer_schedules (
    id bigserial primary key,
    offer_id bigint not null references offers(id) on delete cascade,
    day_of_week int not null check (day_of_week between 1 and 7),
    open_from int not null check (open_from between 0 and 1439),
    open_to int not null check (open_to between 1 and 1440),
    check (open_to > open_from)
);
create table price_periods (
    id bigserial primary key,
    offer_id bigint not null references offers(id) on delete cascade,
    date_from date not null,
    date_to date not null,
    coefficient numeric(5,3) not null check (coefficient > 0),
    check (date_to >= date_from)
);
create table offer_availability (
    id bigserial primary key,
    offer_id bigint not null references offers(id) on delete cascade,
    date date not null,
    quota_total int not null check (quota_total >= 0),
    quota_free int not null check (quota_free >= 0),
    version int not null default 0,
    check (quota_free <= quota_total),
    unique (offer_id, date)
);
create table offer_holds (
    id bigserial primary key,
    booking_ref varchar(64) not null,
    offer_id bigint not null references offers(id),
    date date not null,
    qty int not null check (qty > 0),
    status varchar(12) not null check (status in ('ACTIVE','CONVERTED','RELEASED')),
    expires_at timestamptz not null,
    created_at timestamptz not null default now()
);
create unique index uq_active_hold on offer_holds (booking_ref, offer_id, date) where status in ('ACTIVE','CONVERTED');
create table offer_ratings (
    id bigserial primary key,
    offer_id bigint not null references offers(id) on delete cascade,
    user_ref varchar(64) not null,
    score int not null check (score between 1 and 5),
    unique (offer_id, user_ref)
);
create table outbox_events (
    id bigserial primary key,
    topic varchar(100) not null,
    event_key varchar(100),
    payload text not null,
    created_at timestamptz not null default now(),
    published_at timestamptz
);
create index idx_outbox_unpublished on outbox_events(id) where published_at is null;
