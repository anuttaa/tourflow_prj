create table notification_templates (
    id serial primary key,
    code varchar(80) not null unique,
    subject_tpl varchar(300) not null,
    body_tpl text not null,
    active boolean not null default true
);
create table notifications (
    id bigserial primary key,
    notification_template_id int references notification_templates(id),
    event_id varchar(60) not null,
    event_type varchar(100) not null,
    recipient varchar(255) not null,
    subject varchar(300) not null,
    body text not null,
    status varchar(10) not null check (status in ('SENT','FAILED')),
    error varchar(500),
    created_at timestamptz not null default now(),
    unique (event_id, recipient)
);
create table user_contacts (
    user_ref uuid primary key,
    email varchar(255) not null,
    first_name varchar(100),
    updated_at timestamptz not null default now()
);
insert into notification_templates (code, subject_tpl, body_tpl) values
('user.registered', 'TourFlow: подтвердите email', E'Здравствуйте, {{firstName}}!\n\nДля завершения регистрации перейдите по ссылке:\n{{confirmUrl}}\n\nСсылка действует 24 часа. Если вы не регистрировались, проигнорируйте письмо.'),
('user.blocked', 'TourFlow: учётная запись заблокирована', E'Ваша учётная запись {{email}} заблокирована администратором. По вопросам обратитесь в поддержку.'),
('tour.published', 'Тур опубликован: {{title}}', E'Тур «{{title}}» (версия {{versionNo}}) опубликован на витрине. Стоимость для группы: {{totalPrice}}.'),
('tour.validation.failed', 'Публикация тура отклонена: {{title}}', E'При публикации тура «{{title}}» найдены ошибки в расписании: {{errors}}. Откройте конструктор тура, посмотрите конфликты и воспользуйтесь подбором альтернатив.'),
('booking.created', 'Заявка {{ref}} создана', E'Заявка {{ref}} на тур «{{title}}» создана: {{pax}} чел., сумма {{totalPrice}}.\nМеста удержаны до {{holdExpiresAt}}. Менеджер подтвердит заявку в ближайшее время.'),
('booking.status.changed', 'Заявка {{ref}}: статус {{status}}', E'Статус заявки {{ref}} («{{tourTitle}}») изменился: {{fromStatus}} → {{status}}.\n{{reason}}'),
('booking.status.changed:CONFIRMED', 'Заявка {{ref}} подтверждена', E'Заявка {{ref}} на тур «{{tourTitle}}» подтверждена менеджером. Теперь её можно оплатить в личном кабинете.'),
('booking.status.changed:REJECTED', 'Заявка {{ref}} отклонена', E'К сожалению, заявка {{ref}} на тур «{{tourTitle}}» отклонена. {{reason}}'),
('booking.status.changed:EXPIRED', 'Заявка {{ref}}: истёк срок удержания мест', E'Срок удержания мест по заявке {{ref}} («{{tourTitle}}») истёк, заявка закрыта. Вы можете оформить новую.'),
('booking.status.changed:PAID', 'Заявка {{ref}} оплачена', E'Оплата заявки {{ref}} на тур «{{tourTitle}}» получена. Сумма: {{totalPrice}}. Приятной поездки!'),
('booking.cancelled', 'Заявка {{ref}} отменена', E'Заявка {{ref}} на тур «{{tourTitle}}» отменена. Места освобождены. {{reason}}');
