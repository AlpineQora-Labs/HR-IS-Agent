-- =====================================================================
-- Olivia · core schema (V1)
-- A best-in-class conversational recruiting platform, modeled as a
-- composite of the category leaders in the feature matrix:
--   Paradox  — conversational apply, native ATS, scheduling, offers, onboarding, events, multilingual
--   Phenom   — personalized career sites, talent CRM / nurture
--   Eightfold— skills graph, AI matching, sourcing, internal mobility
--   Humanly  — AI interviewer
--   HireVue  — video interviews, assessments, bias/compliance
--   Sense    — voice screening, referrals, surveys/sentiment, AI copilot
-- All timestamps are timestamptz; ids are uuid (app- or db-generated).
-- =====================================================================

-- ---- People on the hiring side (recruiters / hiring managers / admins) ----
create table recruiter_user (
    id          uuid primary key default gen_random_uuid(),
    name        text not null,
    initials    text not null,
    email       text not null unique,
    role        text not null,                 -- ADMIN | RECRUITER | HIRING_MANAGER | COORDINATOR | VIEWER
    title       text,
    created_at  timestamptz not null default now()
);

-- ---- Skills graph (Eightfold) ----
create table skill (
    id          uuid primary key default gen_random_uuid(),
    name        text not null unique,
    category    text not null                  -- e.g. Technical, Operational, Leadership, Compliance
);

-- A coarse adjacency for "skills adjacency" matching.
create table skill_adjacency (
    skill_id        uuid not null references skill(id) on delete cascade,
    related_skill_id uuid not null references skill(id) on delete cascade,
    weight          numeric(3,2) not null default 0.50,
    primary key (skill_id, related_skill_id)
);

-- ---- Jobs / requisitions (Paradox ATS + Phenom career site) ----
create table job (
    id              uuid primary key default gen_random_uuid(),
    title           text not null,
    department      text not null,
    location        text not null,
    work_mode       text not null,             -- ONSITE | HYBRID | REMOTE
    employment_type text not null,             -- FULL_TIME | PART_TIME | CONTRACT | SEASONAL
    family          text not null,             -- HOURLY | CORPORATE | EARLY_CAREER | TECH
    status          text not null,             -- DRAFT | OPEN | PAUSED | FILLED | CLOSED
    openings        int not null default 1,
    hiring_manager_id uuid references recruiter_user(id),
    recruiter_id    uuid references recruiter_user(id),
    summary         text,                       -- short tagline for the career site
    description     text,                       -- full JD body
    pay_min         numeric(12,2),
    pay_max         numeric(12,2),
    pay_period      text,                       -- HOUR | YEAR
    -- Immersive job preview (Paradox — "Immersive Job Preview")
    preview_headline text,
    preview_media_url text,
    preview_day_in_life text,
    -- personalization / career-site
    hero_image_url  text,
    languages       text,                       -- comma list of supported languages
    posted_at       timestamptz,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create table job_skill (
    job_id      uuid not null references job(id) on delete cascade,
    skill_id    uuid not null references skill(id) on delete cascade,
    weight      numeric(3,2) not null default 1.0,
    required    boolean not null default false,
    primary key (job_id, skill_id)
);

-- Knockout screening (Paradox — configurable knockout criteria)
create table knockout_question (
    id          uuid primary key default gen_random_uuid(),
    job_id      uuid not null references job(id) on delete cascade,
    ordinal     int not null,
    prompt      text not null,
    answer_type text not null,                 -- BOOLEAN | NUMERIC | CHOICE | TEXT
    choices     text,                          -- comma list when CHOICE
    -- rule that DISQUALIFIES: operator+value, e.g. 'EQ:false', 'LT:18', 'NEQ:Yes'
    disqualify_op text,                        -- EQ | NEQ | LT | LTE | GT | GTE | NOT_IN
    disqualify_value text,
    required    boolean not null default true
);

-- ---- Candidates ----
create table candidate (
    id              uuid primary key default gen_random_uuid(),
    name            text not null,
    email           text not null,
    phone           text,
    location        text,
    headline        text,
    years_experience numeric(4,1) not null default 0,
    source          text not null,             -- CAREER_SITE | REFERRAL | SOURCED | EVENT | CAMPUS | DATABASE | AGENCY
    preferred_language text not null default 'en',
    lifecycle       text not null default 'ACTIVE', -- ACTIVE | PASSIVE | DORMANT  (Eightfold DB reactivation)
    last_active_at  timestamptz,
    created_at      timestamptz not null default now()
);

create table candidate_skill (
    candidate_id uuid not null references candidate(id) on delete cascade,
    skill_id     uuid not null references skill(id) on delete cascade,
    proficiency  int not null default 3,       -- 1..5
    years        numeric(4,1) not null default 0,
    inferred     boolean not null default false, -- skills-graph inferred vs stated
    primary key (candidate_id, skill_id)
);

-- ---- Applications (the pipeline spine) ----
create table application (
    id              uuid primary key default gen_random_uuid(),
    candidate_id    uuid not null references candidate(id) on delete cascade,
    job_id          uuid not null references job(id) on delete cascade,
    stage           text not null,             -- APPLIED|SCREENED|MATCHED|INTERVIEW|ASSESSMENT|OFFER|HIRED|REJECTED|WITHDRAWN
    source          text not null,             -- channel the application came in on
    fit_score       int,                       -- 0..100 (Eightfold matching)
    fit_explanation text,
    knockout_passed boolean,
    rejection_reason text,
    applied_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    unique (candidate_id, job_id)
);

create table screening_answer (
    id            uuid primary key default gen_random_uuid(),
    application_id uuid not null references application(id) on delete cascade,
    question_id   uuid not null references knockout_question(id) on delete cascade,
    answer        text,
    passed        boolean
);

-- ---- Conversations (Paradox multi-channel + Olivia; Sense voice) ----
create table conversation (
    id            uuid primary key default gen_random_uuid(),
    candidate_id  uuid references candidate(id) on delete cascade,
    application_id uuid references application(id) on delete cascade,
    job_id        uuid references job(id) on delete set null,
    channel       text not null,               -- WEB_CHAT | SMS | WHATSAPP | EMAIL | VOICE
    language      text not null default 'en',
    kind          text not null,               -- APPLY | SCREEN | SCHEDULE | NURTURE | SUPPORT
    status        text not null default 'OPEN',-- OPEN | COMPLETED | ABANDONED
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

create table message (
    id              uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references conversation(id) on delete cascade,
    sender          text not null,             -- OLIVIA | CANDIDATE | RECRUITER | SYSTEM
    body            text not null,
    intent          text,                       -- detected intent / step tag
    created_at      timestamptz not null default now()
);

-- ---- Self-scheduling + interviews (Paradox scheduling, Humanly AI interviewer, HireVue video) ----
create table interview_slot (
    id            uuid primary key default gen_random_uuid(),
    job_id        uuid references job(id) on delete cascade,
    interviewer_id uuid references recruiter_user(id),
    starts_at     timestamptz not null,
    ends_at       timestamptz not null,
    booked        boolean not null default false
);

create table interview (
    id              uuid primary key default gen_random_uuid(),
    application_id  uuid not null references application(id) on delete cascade,
    slot_id         uuid references interview_slot(id),
    type            text not null,             -- AI_PHONE_SCREEN | AI_INTERVIEW | ONE_WAY_VIDEO | LIVE_VIDEO | PANEL | ONSITE
    scheduled_at    timestamptz,
    duration_min    int not null default 30,
    status          text not null default 'SCHEDULED', -- SCHEDULED | COMPLETED | NO_SHOW | CANCELED
    interviewers    text,                       -- comma list of names
    score           int,                        -- 0..100
    recommendation  text,                       -- ADVANCE | HOLD | REJECT
    summary         text,                       -- AI transcript summary (Humanly)
    created_at      timestamptz not null default now()
);

-- ---- Assessments (HireVue) ----
create table assessment (
    id              uuid primary key default gen_random_uuid(),
    application_id  uuid not null references application(id) on delete cascade,
    type            text not null,             -- COGNITIVE | CODING | GAME | JOB_TRYOUT
    name            text not null,
    status          text not null default 'ASSIGNED', -- ASSIGNED | IN_PROGRESS | COMPLETED | EXPIRED
    score           int,                        -- 0..100
    percentile      int,
    assigned_at     timestamptz not null default now(),
    completed_at    timestamptz
);

-- ---- Offers (Paradox offer generation) ----
create table offer (
    id              uuid primary key default gen_random_uuid(),
    application_id  uuid not null references application(id) on delete cascade,
    title           text not null,
    comp_base       numeric(12,2),
    comp_period     text,                       -- HOUR | YEAR
    comp_bonus      numeric(12,2),
    equity          text,
    start_date      date,
    status          text not null default 'DRAFT', -- DRAFT | SENT | ACCEPTED | DECLINED | EXPIRED
    letter_body     text,
    sent_at         timestamptz,
    responded_at    timestamptz,
    created_at      timestamptz not null default now()
);

-- ---- Onboarding (Paradox onboarding) ----
create table onboarding_task (
    id              uuid primary key default gen_random_uuid(),
    application_id  uuid not null references application(id) on delete cascade,
    name            text not null,
    category        text not null,             -- DOCS | IT | COMMS | DAY_ONE | COMPLIANCE
    status          text not null default 'PENDING', -- PENDING | SENT | COMPLETED
    due_date        date,
    completed_at    timestamptz
);

-- ---- Talent CRM / pipelines / nurture (Phenom) ----
create table talent_pool (
    id          uuid primary key default gen_random_uuid(),
    name        text not null,
    description text,
    created_at  timestamptz not null default now()
);

create table talent_pool_member (
    pool_id      uuid not null references talent_pool(id) on delete cascade,
    candidate_id uuid not null references candidate(id) on delete cascade,
    added_at     timestamptz not null default now(),
    primary key (pool_id, candidate_id)
);

create table nurture_campaign (
    id          uuid primary key default gen_random_uuid(),
    name        text not null,
    audience    text not null,                 -- description of the segment
    channel     text not null,                 -- EMAIL | SMS | WHATSAPP
    status      text not null default 'DRAFT', -- DRAFT | RUNNING | PAUSED | DONE
    sent        int not null default 0,
    opened      int not null default 0,
    replied     int not null default 0,
    created_at  timestamptz not null default now()
);

-- ---- Employee referrals (Sense) ----
create table referral (
    id            uuid primary key default gen_random_uuid(),
    referrer_name text not null,
    referrer_email text,
    candidate_id  uuid references candidate(id) on delete set null,
    job_id        uuid references job(id) on delete set null,
    link_code     text not null unique,
    status        text not null default 'SUBMITTED', -- SUBMITTED | IN_REVIEW | HIRED | CLOSED
    bonus_amount  numeric(12,2),
    created_at    timestamptz not null default now()
);

-- ---- Events / campus (Paradox) ----
create table recruiting_event (
    id            uuid primary key default gen_random_uuid(),
    name          text not null,
    type          text not null,               -- HIRING_EVENT | CAMPUS | WEBINAR | CAREER_FAIR
    location      text,
    starts_at     timestamptz not null,
    registrations int not null default 0,
    attended      int not null default 0,
    hires         int not null default 0
);

-- ---- Surveys & sentiment (Sense) ----
create table survey (
    id          uuid primary key default gen_random_uuid(),
    name        text not null,
    stage       text not null,                 -- APPLICATION | INTERVIEW | OFFER | ONBOARDING | CANDIDATE_NPS
    sent        int not null default 0,
    responses   int not null default 0,
    avg_sentiment numeric(4,2),                -- -1.00 .. 1.00
    nps         int
);

create table survey_response (
    id          uuid primary key default gen_random_uuid(),
    survey_id   uuid not null references survey(id) on delete cascade,
    candidate_id uuid references candidate(id) on delete set null,
    rating      int,                            -- 1..10
    sentiment   numeric(4,2),                   -- -1.00 .. 1.00
    verbatim    text,
    created_at  timestamptz not null default now()
);

-- ---- Internal mobility / talent marketplace (Eightfold) ----
create table internal_employee (
    id          uuid primary key default gen_random_uuid(),
    name        text not null,
    current_title text not null,
    department  text not null,
    tenure_years numeric(4,1) not null default 0,
    location    text
);

create table internal_employee_skill (
    employee_id uuid not null references internal_employee(id) on delete cascade,
    skill_id    uuid not null references skill(id) on delete cascade,
    proficiency int not null default 3,
    primary key (employee_id, skill_id)
);

create table mobility_match (
    id          uuid primary key default gen_random_uuid(),
    employee_id uuid not null references internal_employee(id) on delete cascade,
    job_id      uuid not null references job(id) on delete cascade,
    fit_score   int not null,                  -- 0..100
    rationale   text
);

-- ---- AI copilot artifacts (Sense — generated JD/message/etc.) ----
create table copilot_artifact (
    id          uuid primary key default gen_random_uuid(),
    kind        text not null,                 -- JD | OUTREACH | INTERVIEW_QUESTIONS | SUMMARY | REWRITE
    job_id      uuid references job(id) on delete set null,
    prompt      text,
    content     text not null,
    created_by  uuid references recruiter_user(id),
    created_at  timestamptz not null default now()
);

-- ---- Integrations (Paradox open API / ATS integrations) ----
create table integration (
    id          uuid primary key default gen_random_uuid(),
    name        text not null,                 -- Workday, Greenhouse, iCIMS, SAP, Oracle, Slack, ...
    category    text not null,                 -- ATS | HRIS | CALENDAR | MESSAGING | BI | ASSESSMENT
    status      text not null default 'CONNECTED', -- CONNECTED | AVAILABLE | ERROR
    last_sync_at timestamptz
);

-- ---- Audit + bias/compliance (HireVue) ----
create table audit_event (
    id          uuid primary key default gen_random_uuid(),
    actor       text,
    action      text not null,
    entity_type text,
    entity_id   uuid,
    detail      text,
    created_at  timestamptz not null default now()
);

create table bias_report (
    id            uuid primary key default gen_random_uuid(),
    job_id        uuid references job(id) on delete cascade,
    stage         text not null,               -- which funnel stage
    dimension     text not null,               -- GENDER | ETHNICITY | AGE | OVERALL
    group_label   text not null,
    pass_rate     numeric(5,2) not null,       -- %
    impact_ratio  numeric(5,2),                -- vs reference group (4/5ths rule)
    flagged       boolean not null default false,
    created_at    timestamptz not null default now()
);

-- ---- Daily metrics rollup (Paradox analytics — "1,000+ metrics") ----
create table metric_daily (
    id          uuid primary key default gen_random_uuid(),
    metric_date date not null,
    metric_key  text not null,                 -- applications, screened, scheduled, hires, time_to_hire_days, ...
    value       numeric(14,2) not null,
    unique (metric_date, metric_key)
);

-- ---- Helpful indexes ----
create index idx_application_stage on application(stage);
create index idx_application_job on application(job_id);
create index idx_application_candidate on application(candidate_id);
create index idx_message_conversation on message(conversation_id);
create index idx_conversation_candidate on conversation(candidate_id);
create index idx_interview_application on interview(application_id);
create index idx_job_status on job(status);
create index idx_candidate_lifecycle on candidate(lifecycle);
