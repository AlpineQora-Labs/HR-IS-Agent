-- Campus events, second pass.
--
-- 1. A registration form per event: an event can use any Student registration form in the
--    library (one per country, say). Null: the default form.
-- 2. One student, two addresses. A student signs up with a school email and later applies with
--    a personal one. The candidate record keeps the second address so both lead to one person,
--    and the roster keeps what the student gave at the event.

alter table recruiting_event
    add column registration_form_id uuid references form_definition(id) on delete set null;

alter table candidate add column alt_email text;
create index idx_candidate_alt_email on candidate (lower(alt_email)) where alt_email is not null;
create index idx_candidate_email_lower on candidate (lower(email));

alter table event_registration
    add column personal_email text,
    add column phone          text;
