create table tours (
    id bigserial primary key,
    code varchar(30) not null unique,
    title varchar(200) not null,
    destination_ref bigint not null,
    destination_name varchar(120) not null,
    destination_tz varchar(60) not null,
    manager_ref uuid not null,
    manager_email varchar(255),
    status varchar(12) not null check (status in ('DRAFT','VALIDATED','PUBLISHED','ARCHIVED')),
    current_version_id bigint,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create table tour_versions (
    id bigserial primary key,
    tour_id bigint not null references tours(id) on delete cascade,
    version_no int not null,
    start_date date not null,
    end_date date not null,
    pax int not null check (pax > 0),
    budget numeric(12,2) check (budget is null or budget >= 0),
    profile varchar(10) not null check (profile in ('ECONOMY','BALANCE','COMFORT')),
    total_price numeric(12,2) not null default 0,
    status varchar(12) not null check (status in ('DRAFT','VALIDATED','PUBLISHED','ARCHIVED')),
    created_by uuid,
    created_at timestamptz not null default now(),
    unique (tour_id, version_no),
    check (end_date > start_date)
);
alter table tours add constraint fk_tours_current_version foreign key (current_version_id) references tour_versions(id);
create table tour_segments (
    id bigserial primary key,
    version_id bigint not null references tour_versions(id) on delete cascade,
    offer_ref bigint not null,
    type varchar(20) not null,
    title varchar(200) not null,
    start_utc timestamptz not null,
    end_utc timestamptz not null,
    tz varchar(60) not null,
    place_from varchar(200), place_to varchar(200),
    from_lat double precision, from_lon double precision, to_lat double precision, to_lon double precision,
    international boolean not null default false,
    early_check_in boolean not null default false,
    check_in_from int, check_out_until int,
    open_from int, open_to int, open_days varchar(20),
    class_level int not null default 3,
    rating numeric(3,2) not null default 0,
    unit_price numeric(12,2) not null check (unit_price >= 0),
    season_k numeric(6,3) not null default 1,
    units_per_pax int not null default 1 check (units_per_pax > 0),
    capacity int,
    free_snapshot int,
    stale boolean not null default false,
    position int not null default 0,
    check (end_utc > start_utc)
);
create index idx_segments_version on tour_segments(version_id);
create index idx_segments_offer on tour_segments(offer_ref);
create index idx_segments_range on tour_segments using gist (tstzrange(start_utc, end_utc));
create table segment_links (
    id bigserial primary key,
    version_id bigint not null references tour_versions(id) on delete cascade,
    from_segment_id bigint not null references tour_segments(id) on delete cascade,
    to_segment_id bigint not null references tour_segments(id) on delete cascade,
    required_min int not null,
    slack_min int not null
);
create table validation_rules (
    id bigserial primary key,
    code varchar(10) not null,
    param_name varchar(60) not null unique,
    value double precision not null,
    severity varchar(8) not null default 'ERROR' check (severity in ('ERROR','WARNING')),
    description varchar(300),
    active boolean not null default true
);
create table validation_runs (
    id bigserial primary key,
    version_id bigint not null references tour_versions(id) on delete cascade,
    run_at timestamptz not null default now(),
    errors_count int not null,
    warnings_count int not null,
    refreshed boolean not null default false,
    catalog_available boolean not null default true
);
create table validation_conflicts (
    id bigserial primary key,
    run_id bigint not null references validation_runs(id) on delete cascade,
    rule_code varchar(10) not null,
    severity varchar(8) not null check (severity in ('ERROR','WARNING')),
    segment_a_id bigint references tour_segments(id) on delete set null,
    segment_b_id bigint references tour_segments(id) on delete set null,
    violation_min bigint not null default 0,
    hint varchar(600)
);
create index idx_conflicts_run on validation_conflicts(run_id);
create table alternative_requests (
    id bigserial primary key,
    version_id bigint not null references tour_versions(id) on delete cascade,
    segment_id bigint references tour_segments(id) on delete set null,
    original_offer_ref bigint not null,
    segment_type varchar(20) not null,
    profile varchar(10) not null,
    window_delta_min int not null,
    requested_by uuid,
    created_at timestamptz not null default now()
);
create table alternative_candidates (
    id bigserial primary key,
    request_id bigint not null references alternative_requests(id) on delete cascade,
    offer_ref bigint not null,
    score numeric(6,4) not null,
    rank int not null,
    explanation jsonb not null,
    decision varchar(10) not null default 'NEW' check (decision in ('NEW','ACCEPTED','REJECTED'))
);
create table tour_templates (
    id bigserial primary key,
    name varchar(150) not null,
    source_tour_id bigint references tours(id) on delete set null,
    owner_ref uuid not null,
    params jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
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

insert into validation_rules (code, param_name, value, severity, description) values
('VR-01','RULE_VR-01',1,'ERROR','Сегменты группы не пересекаются по времени'),
('VR-02','BUF_FLIGHT_FLIGHT_INTL',120,'ERROR','Пересадка между международными рейсами, мин'),
('VR-02','BUF_FLIGHT_FLIGHT_DOM',90,'ERROR','Пересадка между внутренними рейсами, мин'),
('VR-02','BUF_FLIGHT_ANY',45,'ERROR','После прилёта до следующего сегмента (багаж), мин'),
('VR-02','BUF_ANY_FLIGHT',90,'ERROR','До вылета рейса (регистрация), мин'),
('VR-02','BUF_STATION_ANY',30,'ERROR','Вокзал или автостанция, мин'),
('VR-02','BUF_TRANSFER_ANY',15,'ERROR','Трансфер, мин'),
('VR-02','BUF_DEFAULT',10,'ERROR','Остальные стыковки, мин'),
('VR-02','WARN_SLACK_RATIO',0.25,'WARNING','Предупреждать, если запас меньше доли от минимума'),
('VR-03','RULE_VR-03',1,'ERROR','Время в UTC с IANA-зоной, окончание позже начала'),
('VR-04','SPEED_KMH',30,'ERROR','Средняя скорость перемещения между точками, км/ч'),
('VR-05','RULE_VR-05',1,'ERROR','Сегмент в часы работы объекта'),
('VR-06','CHECKIN_FROM',840,'ERROR','Заселение не раньше (минуты от полуночи)'),
('VR-06','CHECKOUT_UNTIL',720,'ERROR','Выселение не позже (минуты от полуночи)'),
('VR-07','RULE_VR-07',1,'ERROR','Каждая ночь покрыта проживанием или ночной перевозкой'),
('VR-08','RULE_VR-08',1,'ERROR','Сегменты внутри дат тура, первый и последний — перевозка'),
('VR-09','RULE_VR-09',1,'ERROR','Вместимость и остаток квоты'),
('VR-10','MAX_DAILY_ACTIVE_MIN',600,'WARNING','Максимальная суточная нагрузка, мин'),
('VR-10','MIN_BREAK_MIN',60,'WARNING','Минимальный перерыв между экскурсиями, мин'),
('BR-01','BUDGET_TOLERANCE',0.10,'ERROR','Допустимое превышение бюджета (доля)');
