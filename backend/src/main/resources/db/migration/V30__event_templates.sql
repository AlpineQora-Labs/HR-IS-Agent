-- Event templates: everything that is the same for a kind of event ("Campus drive, India"),
-- kept once. A new event starts from one and takes its settings; from then on the event's
-- settings are its own, so changing a template later changes only events created afterwards.

create table event_template (
    id                   uuid primary key default gen_random_uuid(),
    name                 text not null unique,
    event_type           text not null default 'CAMPUS',        -- CAMPUS | CAREER_FAIR | HIRING_EVENT | WEBINAR
    format               text not null default 'IN_PERSON'
        check (format in ('IN_PERSON', 'VIRTUAL', 'HYBRID')),
    country              varchar(2),                             -- ISO code; null for any
    timezone             varchar(60) not null default 'America/New_York',
    -- Forms point at the library, so correcting a form corrects every template that uses it.
    intake_form_id       uuid references form_definition(id) on delete set null,
    registration_form_id uuid references form_definition(id) on delete set null,
    capacity             int check (capacity is null or capacity > 0),
    waitlist_enabled     boolean not null default false,
    -- Registration closes this many days before the event starts; null: open until it starts.
    close_days_before    int check (close_days_before is null or close_days_before >= 0),
    -- The event goes on the careers site this many days before it starts (once approved);
    -- null: as soon as it is approved.
    publish_days_before  int check (publish_days_before is null or publish_days_before >= 0),
    -- What a student agrees to; null for the standard wording.
    consent_text         text,
    active               boolean not null default true,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now()
);

-- What an event took from its template stays on the event.
alter table recruiting_event
    add column template_id  uuid references event_template(id) on delete set null,
    add column country      varchar(2),
    add column format       text,
    add column consent_text text,
    -- When the event goes on the careers site. It must also be approved: an approved event
    -- shows from this moment, an unapproved one never. Null: as soon as it is approved.
    add column publish_at   timestamptz;

insert into event_template (name, event_type, format, country, timezone, capacity, waitlist_enabled, close_days_before, publish_days_before) values
 ('Campus drive, US',         'CAMPUS',  'IN_PERSON', 'US', 'America/New_York', 150, true,  2,    30),
 ('Campus drive, India',      'CAMPUS',  'IN_PERSON', 'IN', 'Asia/Kolkata',     300, true,  3,    30),
 ('Campus drive, UK',         'CAMPUS',  'IN_PERSON', 'GB', 'Europe/London',    120, true,  2,    30),
 ('Virtual info session',     'WEBINAR', 'VIRTUAL',   null, 'America/New_York', null, false, null, 14);
