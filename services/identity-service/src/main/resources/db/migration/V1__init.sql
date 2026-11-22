create table roles (
    id serial primary key,
    code varchar(30) not null unique
);
create table users (
    id uuid primary key,
    email varchar(255) not null unique,
    password_hash varchar(100) not null,
    status varchar(20) not null check (status in ('PENDING','ACTIVE','BLOCKED')),
    failed_attempts int not null default 0,
    locked_until timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create table user_roles (
    user_id uuid not null references users(id) on delete cascade,
    role_id int not null references roles(id),
    primary key (user_id, role_id)
);
create table refresh_tokens (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    token_hash varchar(64) not null unique,
    expires_at timestamptz not null,
    revoked boolean not null default false,
    created_at timestamptz not null default now()
);
create table email_confirmations (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    token_hash varchar(64) not null unique,
    expires_at timestamptz not null,
    confirmed_at timestamptz
);
create table user_profiles (
    user_id uuid primary key references users(id) on delete cascade,
    first_name varchar(100),
    last_name varchar(100),
    phone varchar(30)
);
create table traveler_preferences (
    user_id uuid primary key references users(id) on delete cascade,
    budget numeric(12,2) check (budget is null or budget >= 0),
    class_level int check (class_level is null or class_level between 1 and 5),
    interests varchar(500),
    profile varchar(20) not null default 'BALANCE' check (profile in ('ECONOMY','BALANCE','COMFORT'))
);
create table audit_log (
    id bigserial primary key,
    actor uuid,
    action varchar(50) not null,
    target varchar(100),
    details varchar(1000),
    created_at timestamptz not null default now()
);
create index idx_audit_created on audit_log(created_at desc);
create table outbox_events (
    id bigserial primary key,
    topic varchar(100) not null,
    event_key varchar(100),
    payload text not null,
    created_at timestamptz not null default now(),
    published_at timestamptz
);
create index idx_outbox_unpublished on outbox_events(id) where published_at is null;
