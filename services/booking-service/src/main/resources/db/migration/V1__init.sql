create table bookings (
    id bigserial primary key,
    ref varchar(40) not null unique,
    user_ref uuid not null,
    user_email varchar(255),
    tour_id bigint not null,
    version_id bigint not null,
    tour_title varchar(200) not null,
    destination_name varchar(120) not null,
    start_date date not null,
    end_date date not null,
    pax int not null check (pax > 0 and pax <= 50),
    status varchar(12) not null check (status in ('PENDING','CONFIRMED','REJECTED','EXPIRED','PAID','COMPLETED','CANCELLED')),
    total_price numeric(12,2) not null check (total_price >= 0),
    penalty numeric(12,2) not null default 0 check (penalty >= 0),
    hold_expires_at timestamptz,
    comment varchar(500),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index idx_bookings_user on bookings(user_ref);
create index idx_bookings_status on bookings(status, hold_expires_at);
create table booking_items (
    id bigserial primary key,
    booking_id bigint not null references bookings(id) on delete cascade,
    offer_ref bigint not null,
    type varchar(20) not null,
    title varchar(200) not null,
    unit_price numeric(12,2) not null,
    season_k numeric(6,3) not null default 1,
    units_per_pax int not null,
    dates varchar(400) not null,
    line_total numeric(12,2) not null
);
create table booking_travelers (
    id bigserial primary key,
    booking_id bigint not null references bookings(id) on delete cascade,
    full_name varchar(150) not null,
    birth_date date
);
create table booking_status_history (
    id bigserial primary key,
    booking_id bigint not null references bookings(id) on delete cascade,
    from_status varchar(12),
    to_status varchar(12) not null,
    changed_by uuid,
    reason varchar(500),
    created_at timestamptz not null default now()
);
create table payments (
    id bigserial primary key,
    booking_id bigint not null references bookings(id) on delete cascade,
    kind varchar(10) not null check (kind in ('PAYMENT','REFUND')),
    amount numeric(12,2) not null check (amount >= 0),
    status varchar(10) not null check (status in ('SUCCEEDED','FAILED')),
    provider varchar(30) not null default 'sandbox',
    external_id varchar(60),
    created_at timestamptz not null default now()
);
create table cancellation_policies (
    id serial primary key,
    days_before_min int not null unique check (days_before_min >= 0),
    penalty_percent numeric(5,2) not null check (penalty_percent between 0 and 100)
);
insert into cancellation_policies (days_before_min, penalty_percent) values (30, 0), (14, 30), (7, 50), (0, 100);
create table outbox_events (
    id bigserial primary key,
    topic varchar(100) not null,
    event_key varchar(100),
    payload text not null,
    created_at timestamptz not null default now(),
    published_at timestamptz
);
create index idx_outbox_unpublished on outbox_events(id) where published_at is null;
