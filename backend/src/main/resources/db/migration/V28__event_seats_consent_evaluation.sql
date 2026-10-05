-- Campus events, tightened.
--
-- 1. Seats. Capacity, a registration deadline and a waitlist were questions on the intake form
--    whose answers nothing read. They are now settings of the event, and registration keeps to
--    them: full means waitlisted (or refused, without a waitlist), late means refused.
-- 2. Consent. A student's details are kept only with their agreement, and when they gave it.
-- 3. Evaluation. What the recruiter made of a student at the booth (rating, notes, shortlist),
--    and the application made for them when they are put forward for a job.

alter table recruiting_event
    add column capacity               int check (capacity is null or capacity > 0),
    add column registration_closes_at timestamptz,
    add column waitlist_enabled       boolean not null default false;

-- status gains WAITLISTED (no seat yet) and CANCELLED (gave the seat up).
alter table event_registration
    add column consent_at     timestamptz,
    add column rating         int check (rating is null or rating between 1 and 5),
    add column notes          text,
    add column shortlisted    boolean not null default false,
    add column evaluated_at   timestamptz,
    add column application_id uuid references application(id) on delete set null;

create index idx_event_registration_status on event_registration(event_id, status);
