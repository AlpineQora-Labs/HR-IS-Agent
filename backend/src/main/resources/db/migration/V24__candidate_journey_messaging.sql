-- The candidate journey, by text and email: the record of every message, who may
-- be texted, and the wording of each point. See docs/candidate-journey.md.

-- 1. Who can be texted ------------------------------------------------------

-- The number in one standard form (E.164), so that a number typed by a
-- candidate and the number a text arrives from can be compared. Filled only
-- where what was typed is a number a text can reach; anything else stays empty.
ALTER TABLE candidate ADD COLUMN phone_e164 TEXT;
ALTER TABLE candidate ADD COLUMN sms_consent_at TIMESTAMPTZ;
ALTER TABLE candidate ADD CONSTRAINT candidate_phone_e164_format
    CHECK (phone_e164 IS NULL OR phone_e164 ~ '^\+[1-9][0-9]{7,14}$');

WITH typed AS (
    SELECT id,
           regexp_replace(regexp_replace(btrim(phone), '\s*(x|ext\.?|#)\s*[0-9]+\s*$', '', 'i'), '[^0-9]', '', 'g') AS digits,
           btrim(phone) LIKE '+%' AS international,
           regexp_replace(btrim(phone), '\s*(x|ext\.?|#)\s*[0-9]+\s*$', '', 'i') ~ '[^0-9 ().+-]' AS not_a_number
    FROM candidate
    WHERE phone IS NOT NULL AND btrim(phone) <> ''
), standard AS (
    SELECT id, CASE
        WHEN not_a_number THEN NULL
        WHEN international AND length(digits) BETWEEN 8 AND 15 AND left(digits, 1) <> '0' THEN '+' || digits
        WHEN NOT international AND length(digits) = 10 AND left(digits, 1) BETWEEN '2' AND '9' THEN '+1' || digits
        WHEN NOT international AND length(digits) = 11 AND left(digits, 1) = '1'
             AND substr(digits, 2, 1) BETWEEN '2' AND '9' THEN '+' || digits
        ELSE NULL END AS e164
    FROM typed
)
UPDATE candidate c
SET phone_e164 = s.e164
FROM standard s
WHERE s.id = c.id AND s.e164 IS NOT NULL AND s.e164 ~ '^\+[1-9][0-9]{7,14}$';

CREATE INDEX idx_candidate_phone_e164 ON candidate (phone_e164) WHERE phone_e164 IS NOT NULL;

-- Agreement to be texted. The people already here gave their number in answer
-- to "a phone number where we can text or call you", or are sample data: they
-- are recorded as having agreed, from the day they were added. From now on the
-- question names texting and STOP, and agreement is recorded when it is answered.
UPDATE candidate SET sms_consent_at = COALESCE(created_at, now()) WHERE phone_e164 IS NOT NULL;

-- A number that said STOP. Opting out belongs to the number, not to a candidate
-- record: one person can have several records, and a number can say STOP before
-- anyone has applied from it.
CREATE TABLE sms_opt_out (
    phone_e164   TEXT PRIMARY KEY CHECK (phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    opted_out_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Scheduling ---------------------------------------------------------------

-- How many times a candidate is offered to choose from.
ALTER TABLE scheduling_policy
    ADD COLUMN proposal_count INT NOT NULL DEFAULT 3 CHECK (proposal_count BETWEEN 1 AND 6);

-- When the current time was booked. Reminders depend on it: an interview booked
-- less than a day ahead gets no "24 hours before" reminder.
ALTER TABLE interview ADD COLUMN booked_at TIMESTAMPTZ;
UPDATE interview SET booked_at = created_at WHERE scheduled_at IS NOT NULL;

-- One interviewer, one interview at a time. The service holds the interviewer
-- while it books; this is the database's own word on it. Left out only if the
-- data already breaks it, so that the rest of this migration can still apply.
DO $guard$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM calendar_event WHERE kind = 'INTERVIEW'
        GROUP BY user_id, starts_at HAVING count(*) > 1
    ) THEN
        CREATE UNIQUE INDEX uq_calendar_interview_start
            ON calendar_event (user_id, starts_at) WHERE kind = 'INTERVIEW';
    ELSE
        RAISE NOTICE 'calendar_event already holds two interviews for one person at one time: the unique index was not created';
    END IF;
END
$guard$;

-- 3. The record of every message -------------------------------------------------

CREATE TABLE candidate_message (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id        UUID REFERENCES candidate (id) ON DELETE CASCADE,
    application_id      UUID REFERENCES application (id) ON DELETE SET NULL,
    interview_id        UUID REFERENCES interview (id) ON DELETE SET NULL,
    channel             VARCHAR(10)  NOT NULL CHECK (channel IN ('SMS', 'EMAIL')),
    direction           VARCHAR(10)  NOT NULL CHECK (direction IN ('OUTBOUND', 'INBOUND')),
    address             TEXT,
    subject             TEXT,
    body                TEXT         NOT NULL,
    point               VARCHAR(40),
    template_id         UUID,
    template_name       VARCHAR(160),
    status              VARCHAR(12)  NOT NULL
        CHECK (status IN ('QUEUED', 'SENDING', 'SENT', 'FAILED', 'SUPPRESSED', 'RECEIVED')),
    reason              TEXT,
    provider            VARCHAR(40),
    provider_message_id VARCHAR(120),
    dedupe_key          VARCHAR(200),
    offer               TEXT,
    offer_closed_at     TIMESTAMPTZ,
    processed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at             TIMESTAMPTZ,
    -- a text that arrived is RECEIVED and nothing else; a message going out is never RECEIVED
    CONSTRAINT candidate_message_status_fits CHECK ((direction = 'INBOUND') = (status = 'RECEIVED'))
);
CREATE INDEX idx_candidate_message_candidate ON candidate_message (candidate_id, created_at);
CREATE INDEX idx_candidate_message_address ON candidate_message (address, created_at);
CREATE INDEX idx_candidate_message_waiting ON candidate_message (status, created_at)
    WHERE status IN ('QUEUED', 'SENDING');
CREATE INDEX idx_candidate_message_offer ON candidate_message (interview_id)
    WHERE offer IS NOT NULL AND offer_closed_at IS NULL;
-- the same thing is never sent twice
CREATE UNIQUE INDEX uq_candidate_message_dedupe ON candidate_message (dedupe_key) WHERE dedupe_key IS NOT NULL;
-- a text delivered twice by a carrier is one text
CREATE UNIQUE INDEX uq_candidate_message_provider ON candidate_message (provider, provider_message_id)
    WHERE provider_message_id IS NOT NULL;

-- 4. Wording ---------------------------------------------------------------------

-- A template with a key words a point of the journey. It can be reworded but
-- not deleted (TemplateRules), so a point always has something to say.
ALTER TABLE sms_template ADD COLUMN template_key VARCHAR(80);
CREATE UNIQUE INDEX uq_sms_template_key ON sms_template (template_key) WHERE template_key IS NOT NULL;
ALTER TABLE email_template ADD COLUMN template_key VARCHAR(80);
CREATE UNIQUE INDEX uq_email_template_key ON email_template (template_key) WHERE template_key IS NOT NULL;

-- The earlier starter templates are replaced by the journey's. Those nobody has
-- changed are archived; any that were edited are left exactly as they are.
UPDATE sms_template SET status = 'ARCHIVED', category = 'Earlier version', updated_at = now()
WHERE id IN ('70000000-0000-0000-0000-000000000001', '70000000-0000-0000-0000-000000000002',
             '70000000-0000-0000-0000-000000000003', '70000000-0000-0000-0000-000000000004',
             '70000000-0000-0000-0000-000000000005')
  AND updated_at = created_at;
UPDATE email_template SET status = 'ARCHIVED', updated_at = now()
WHERE id IN ('98000000-0000-0000-0000-000000000010', '98000000-0000-0000-0000-000000000011')
  AND updated_at = created_at;

-- Text messages: plain characters only, the sender's name first, times in Eastern Time.

INSERT INTO sms_template (id, template_key, name, body, category, status)
SELECT v.id::uuid, v.template_key, v.name, v.body, 'Candidate journey', 'ACTIVE'
FROM (VALUES
    ('71000000-0000-0000-0000-000000000001', 'journey.application_received', 'Application received',
     '{{company_name}}: Hi {{first_name}}, we received your application for {{job_title}}. Reply STATUS at any time for an update.'),
    ('71000000-0000-0000-0000-000000000002', 'journey.status_reply', 'Status update',
     '{{company_name}}: Hi {{first_name}}. {{status_summary}}'),
    ('71000000-0000-0000-0000-000000000003', 'journey.interview_invite', 'Interview invitation',
     E'{{company_name}}: Good news, {{first_name}}. We would like to interview you for {{job_title}}. Reply {{slot_choices}} to pick a time:\n{{slot_options}}\nReply MORE for other times.'),
    ('71000000-0000-0000-0000-000000000004', 'journey.interview_confirmed', 'Interview confirmed',
     E'{{company_name}}: You are confirmed, {{first_name}}. {{job_title}} interview, {{interview_time}}, with {{interviewers}}. Join: {{meeting_link}}\nReply RESCHEDULE to change it.'),
    ('71000000-0000-0000-0000-000000000005', 'journey.reschedule_options', 'New times after a reschedule',
     E'{{company_name}}: No problem, {{first_name}}. Your earlier time is released. Reply {{slot_choices}} to pick a new time for your {{job_title}} interview:\n{{slot_options}}\nReply MORE for other times.'),
    ('71000000-0000-0000-0000-000000000006', 'journey.interview_reminder_24h', 'Reminder, 24 hours before',
     E'{{company_name}}: A reminder, {{first_name}}. Your {{job_title}} interview is {{interview_time}} with {{interviewers}}. Join: {{meeting_link}}\nReply RESCHEDULE to change it.'),
    ('71000000-0000-0000-0000-000000000007', 'journey.interview_reminder_1h', 'Reminder, 1 hour before',
     '{{company_name}}: Starting soon, {{first_name}}. Your {{job_title}} interview is at {{interview_time}}. Join: {{meeting_link}}'),
    ('71000000-0000-0000-0000-000000000008', 'journey.team_rescheduled', 'Interview moved by the hiring team',
     E'{{company_name}}: {{first_name}}, the hiring team has to move your {{job_title}} interview, and we are sorry for the change. Reply {{slot_choices}} to pick a new time:\n{{slot_options}}\nReply MORE for other times.'),
    ('71000000-0000-0000-0000-000000000009', 'journey.interview_canceled', 'Interview cancelled',
     '{{company_name}}: {{first_name}}, your {{job_title}} interview on {{interview_time}} has been cancelled. Your recruiter will be in touch.'),
    ('71000000-0000-0000-0000-000000000010', 'journey.invite_by_link', 'Interview invitation, by link',
     '{{company_name}}: Good news, {{first_name}}. We would also like to interview you for {{job_title}}. Choose a time here: {{link}}'),
    ('71000000-0000-0000-0000-000000000011', 'journey.more_times', 'Other times',
     E'{{company_name}}: Here are other times for your {{job_title}} interview. Reply {{slot_choices}} to pick one:\n{{slot_options}}\nReply MORE for later times.'),
    ('71000000-0000-0000-0000-000000000012', 'journey.no_other_times', 'No other times',
     E'{{company_name}}: These are the only times open right now for your {{job_title}} interview. Reply {{slot_choices}} to pick one:\n{{slot_options}}'),
    ('71000000-0000-0000-0000-000000000013', 'journey.slot_taken', 'Time no longer available',
     E'{{company_name}}: Sorry, {{first_name}}, that time is no longer available. Reply {{slot_choices}} to pick another for your {{job_title}} interview:\n{{slot_options}}'),
    ('71000000-0000-0000-0000-000000000014', 'journey.no_times_available', 'Times being arranged',
     '{{company_name}}: Thank you, {{first_name}}. We are arranging interview times for {{job_title}} with the hiring team and will text you as soon as they are ready.'),
    ('71000000-0000-0000-0000-000000000015', 'journey.reschedule_declined', 'Reschedule not possible',
     '{{company_name}}: We cannot change this interview by text, {{first_name}}. {{reason}} It stays at {{interview_time}}, and your recruiter will contact you.'),
    ('71000000-0000-0000-0000-000000000016', 'journey.reschedule_no_times', 'Reschedule, no other times',
     '{{company_name}}: There are no other times open right now, {{first_name}}, so your {{job_title}} interview stays at {{interview_time}}. Your recruiter will contact you.'),
    ('71000000-0000-0000-0000-000000000017', 'journey.nothing_to_reschedule', 'Nothing to reschedule',
     '{{company_name}}: You do not have an interview booked right now, {{first_name}}. Reply STATUS to see where your application stands.'),
    ('71000000-0000-0000-0000-000000000018', 'journey.already_booked', 'Already booked',
     '{{company_name}}: You are booked for {{interview_time}}, {{first_name}}. Reply RESCHEDULE to change it.'),
    ('71000000-0000-0000-0000-000000000019', 'journey.pick_a_number', 'Pick a time',
     E'{{company_name}}: Please reply {{slot_choices}} to pick a time, or MORE for other times:\n{{slot_options}}'),
    ('71000000-0000-0000-0000-000000000020', 'journey.help', 'Help',
     '{{company_name}}: Reply STATUS for an update, TIMES to see interview times, RESCHEDULE to move an interview, STOP to opt out. Msg and data rates may apply.'),
    ('71000000-0000-0000-0000-000000000021', 'journey.opt_out', 'Opted out of texts',
     '{{company_name}}: You are unsubscribed and will get no more texts from us. We will still email you. To change an interview, use the link in your email. Reply START to get texts again.'),
    ('71000000-0000-0000-0000-000000000022', 'journey.opt_in', 'Opted back in',
     '{{company_name}}: You are subscribed to texts again. Reply STATUS for an update, HELP for help, STOP to opt out.'),
    ('71000000-0000-0000-0000-000000000023', 'journey.unknown_sender', 'Number not recognised',
     '{{company_name}}: We could not find an application for this number. Please reply from the mobile number you gave when you applied.')
) AS v (id, template_key, name, body)
WHERE NOT EXISTS (SELECT 1 FROM sms_template t WHERE t.template_key = v.template_key)
  AND NOT EXISTS (SELECT 1 FROM sms_template t WHERE t.id = v.id::uuid);

INSERT INTO email_template (id, template_key, name, description, category, subject, from_name, body_html, status)
SELECT v.id::uuid, v.template_key, v.name, v.description, v.category, v.subject, 'Bank of America Careers', v.body_html, 'ACTIVE'
FROM (VALUES
    ('98100000-0000-0000-0000-000000000001', 'journey.application_received', 'Application received', 'Sent when a candidate applies', 'WELCOME',
     'We received your application for {{job_title}}',
     '<h2>Thank you for applying, {{first_name}}</h2><p>We received your application for <strong>{{job_title}}</strong>, and our hiring team is reviewing it.</p><p><strong>What happens next</strong></p><ul><li>We review every application. If you are selected for interview, we will text and email you times to choose from.</li><li>You can check where your application stands at any time by replying STATUS to our text message.</li></ul><p>Regards,<br>{{sender_name}}</p>'),
    ('98100000-0000-0000-0000-000000000002', 'journey.interview_invite', 'Interview invitation', 'Sent when a candidate is selected for interview', 'INVITATION',
     'Choose a time for your {{job_title}} interview',
     '<h2>Good news, {{first_name}}</h2><p>We would like to interview you for <strong>{{job_title}}</strong>. These times work for the hiring team:</p><p>{{slot_options}}</p><p><a class="email-cta-button" href="{{link}}" target="_blank">Choose a time</a></p><p>You can also reply to our text message with the number of the time you prefer. All times are Eastern Time.</p><p>Regards,<br>{{sender_name}}</p>'),
    ('98100000-0000-0000-0000-000000000003', 'journey.interview_confirmed', 'Interview confirmed', 'Sent when an interview is booked', 'INVITATION',
     'Confirmed: your {{job_title}} interview, {{interview_time}}',
     '<h2>Your interview is confirmed</h2><p>Hi {{first_name}}, here are the details.</p><p><strong>Role:</strong> {{job_title}}<br><strong>When:</strong> {{interview_time}}<br><strong>With:</strong> {{interviewers}}</p><p><a class="email-cta-button" href="{{meeting_link}}" target="_blank">Join the interview</a></p><p>Need a different time? <a href="{{link}}" target="_blank">Change your interview</a>, or reply RESCHEDULE to our text message.</p><p>Regards,<br>{{sender_name}}</p>'),
    ('98100000-0000-0000-0000-000000000004', 'journey.interview_reminder_24h', 'Reminder, 24 hours before', 'Sent the day before an interview', 'REMINDER',
     'Reminder: your {{job_title}} interview, {{interview_time}}',
     '<h2>Your interview is coming up</h2><p>Hi {{first_name}}, a reminder of your interview.</p><p><strong>Role:</strong> {{job_title}}<br><strong>When:</strong> {{interview_time}}<br><strong>With:</strong> {{interviewers}}</p><p><a class="email-cta-button" href="{{meeting_link}}" target="_blank">Join the interview</a></p><p>Need a different time? <a href="{{link}}" target="_blank">Change your interview</a>.</p><p>Regards,<br>{{sender_name}}</p>'),
    ('98100000-0000-0000-0000-000000000005', 'journey.team_rescheduled', 'Interview moved by the hiring team', 'Sent when the hiring team moves an interview', 'INVITATION',
     'Your {{job_title}} interview needs a new time',
     '<h2>We need to move your interview</h2><p>Hi {{first_name}}, the hiring team has to move your <strong>{{job_title}}</strong> interview. We are sorry for the change. These times are open:</p><p>{{slot_options}}</p><p><a class="email-cta-button" href="{{link}}" target="_blank">Choose a new time</a></p><p>All times are Eastern Time.</p><p>Regards,<br>{{sender_name}}</p>'),
    ('98100000-0000-0000-0000-000000000006', 'journey.interview_canceled', 'Interview cancelled', 'Sent when the hiring team cancels an interview', 'CUSTOM',
     'Your {{job_title}} interview has been cancelled',
     '<h2>Your interview has been cancelled</h2><p>Hi {{first_name}}, your <strong>{{job_title}}</strong> interview on {{interview_time}} has been cancelled. Your recruiter will be in touch.</p><p>Regards,<br>{{sender_name}}</p>')
) AS v (id, template_key, name, description, category, subject, body_html)
WHERE NOT EXISTS (SELECT 1 FROM email_template t WHERE t.template_key = v.template_key)
  AND NOT EXISTS (SELECT 1 FROM email_template t WHERE t.id = v.id::uuid);

-- 5. The journey, drawn -------------------------------------------------------------

-- The drawing as it stands is kept, switched off, under a name of its own:
-- nothing anyone drew is lost.
INSERT INTO approval_workflows (wf_key, name, trigger_type, enabled, auto_approve, levels_json, graph_json)
SELECT 'cj1-earlier', left(name, 100) || ' (earlier drawing)', trigger_type, false, auto_approve, levels_json, graph_json
FROM approval_workflows
WHERE wf_key = 'cj1' AND graph_json IS NOT NULL
ON CONFLICT (wf_key) DO NOTHING;

-- The journey redrawn, each message step bound to its point and its template.
-- Where the workflow exists only its drawing changes: whether it is switched on
-- stays as it was left. Where it does not, it is added, and switched on only if
-- no other journey is.
INSERT INTO approval_workflows (wf_key, name, trigger_type, enabled, auto_approve, levels_json, graph_json)
VALUES ('cj1', 'Candidate journey', 'Candidate journey',
        NOT EXISTS (SELECT 1 FROM approval_workflows w
                    WHERE lower(w.trigger_type) = 'candidate journey' AND w.enabled AND w.wf_key <> 'cj1'),
        false, '[]', $graph$
{"nodes":[{"id":"trigger","type":"trigger","position":{"x":320,"y":30},"data":{"label":"Candidate applies"}},{"id":"sms-received","type":"sms","position":{"x":320,"y":240},"data":{"label":"Application received","journeyPoint":"APPLICATION_RECEIVED","smsTrigger":"When the candidate applies","smsTemplateId":"71000000-0000-0000-0000-000000000001","smsTemplateName":"Application received"}},{"id":"email-received","type":"email","position":{"x":320,"y":450},"data":{"label":"Application received","journeyPoint":"APPLICATION_RECEIVED","emailTrigger":"When the candidate applies","emailTemplateId":"98100000-0000-0000-0000-000000000001","emailTemplateName":"Application received"}},{"id":"step-review","type":"step","position":{"x":320,"y":660},"data":{"label":"Application under review"}},{"id":"rule-status","type":"condition","position":{"x":320,"y":870},"data":{"condition":"Candidate asks for status"}},{"id":"sms-status","type":"sms","position":{"x":124,"y":1080},"data":{"label":"Status update","journeyPoint":"STATUS_REPLY","smsTrigger":"When the candidate asks for their status","smsTemplateId":"71000000-0000-0000-0000-000000000002","smsTemplateName":"Status update"}},{"id":"step-selected","type":"step","position":{"x":320,"y":1080},"data":{"label":"Selected for interview"}},{"id":"sms-invite","type":"sms","position":{"x":320,"y":1290},"data":{"label":"Interview invitation","journeyPoint":"INTERVIEW_INVITE","smsTrigger":"When the candidate is selected for interview","smsTemplateId":"71000000-0000-0000-0000-000000000003","smsTemplateName":"Interview invitation"}},{"id":"email-invite","type":"email","position":{"x":320,"y":1500},"data":{"label":"Interview invitation","journeyPoint":"INTERVIEW_INVITE","emailTrigger":"When the candidate is selected for interview","emailTemplateId":"98100000-0000-0000-0000-000000000002","emailTemplateName":"Interview invitation"}},{"id":"step-pick","type":"step","position":{"x":320,"y":1710},"data":{"label":"Candidate picks a time"}},{"id":"sms-confirmed","type":"sms","position":{"x":320,"y":1920},"data":{"label":"Interview confirmed","journeyPoint":"INTERVIEW_CONFIRMED","smsTrigger":"When the interview is booked","smsTemplateId":"71000000-0000-0000-0000-000000000004","smsTemplateName":"Interview confirmed"}},{"id":"email-confirmed","type":"email","position":{"x":320,"y":2130},"data":{"label":"Interview confirmed","journeyPoint":"INTERVIEW_CONFIRMED","emailTrigger":"When the interview is booked","emailTemplateId":"98100000-0000-0000-0000-000000000003","emailTemplateName":"Interview confirmed"}},{"id":"rule-reschedule","type":"condition","position":{"x":320,"y":2340},"data":{"condition":"Candidate asks to reschedule"}},{"id":"sms-reschedule","type":"sms","position":{"x":124,"y":2550},"data":{"label":"New times after a reschedule","journeyPoint":"RESCHEDULE_OPTIONS","smsTrigger":"When the candidate asks to reschedule","smsTemplateId":"71000000-0000-0000-0000-000000000005","smsTemplateName":"New times after a reschedule"}},{"id":"sms-remind-24h","type":"sms","position":{"x":320,"y":2550},"data":{"label":"Reminder, 24 hours before","journeyPoint":"INTERVIEW_REMINDER_24H","smsTrigger":"24 hours before the interview","smsTemplateId":"71000000-0000-0000-0000-000000000006","smsTemplateName":"Reminder, 24 hours before"}},{"id":"email-remind-24h","type":"email","position":{"x":320,"y":2760},"data":{"label":"Reminder, 24 hours before","journeyPoint":"INTERVIEW_REMINDER_24H","emailTrigger":"24 hours before the interview","emailTemplateId":"98100000-0000-0000-0000-000000000004","emailTemplateName":"Reminder, 24 hours before"}},{"id":"sms-remind-1h","type":"sms","position":{"x":320,"y":2970},"data":{"label":"Reminder, 1 hour before","journeyPoint":"INTERVIEW_REMINDER_1H","smsTrigger":"1 hour before the interview","smsTemplateId":"71000000-0000-0000-0000-000000000007","smsTemplateName":"Reminder, 1 hour before"}},{"id":"end","type":"end","position":{"x":320,"y":3180},"data":{"label":"Interview complete"}}],"edges":[{"id":"e-trigger-x-sms-received","source":"trigger","target":"sms-received","sourceHandle":null,"targetHandle":null},{"id":"e-sms-received-x-email-received","source":"sms-received","target":"email-received","sourceHandle":null,"targetHandle":null},{"id":"e-email-received-x-step-review","source":"email-received","target":"step-review","sourceHandle":null,"targetHandle":null},{"id":"e-step-review-x-rule-status","source":"step-review","target":"rule-status","sourceHandle":null,"targetHandle":null},{"id":"e-rule-status-yes-sms-status","source":"rule-status","target":"sms-status","sourceHandle":"yes","targetHandle":null},{"id":"e-sms-status-x-step-review","source":"sms-status","target":"step-review","sourceHandle":null,"targetHandle":null},{"id":"e-rule-status-no-step-selected","source":"rule-status","target":"step-selected","sourceHandle":"no","targetHandle":null},{"id":"e-step-selected-x-sms-invite","source":"step-selected","target":"sms-invite","sourceHandle":null,"targetHandle":null},{"id":"e-sms-invite-x-email-invite","source":"sms-invite","target":"email-invite","sourceHandle":null,"targetHandle":null},{"id":"e-email-invite-x-step-pick","source":"email-invite","target":"step-pick","sourceHandle":null,"targetHandle":null},{"id":"e-step-pick-x-sms-confirmed","source":"step-pick","target":"sms-confirmed","sourceHandle":null,"targetHandle":null},{"id":"e-sms-confirmed-x-email-confirmed","source":"sms-confirmed","target":"email-confirmed","sourceHandle":null,"targetHandle":null},{"id":"e-email-confirmed-x-rule-reschedule","source":"email-confirmed","target":"rule-reschedule","sourceHandle":null,"targetHandle":null},{"id":"e-rule-reschedule-yes-sms-reschedule","source":"rule-reschedule","target":"sms-reschedule","sourceHandle":"yes","targetHandle":null},{"id":"e-sms-reschedule-x-step-pick","source":"sms-reschedule","target":"step-pick","sourceHandle":null,"targetHandle":null},{"id":"e-rule-reschedule-no-sms-remind-24h","source":"rule-reschedule","target":"sms-remind-24h","sourceHandle":"no","targetHandle":null},{"id":"e-sms-remind-24h-x-email-remind-24h","source":"sms-remind-24h","target":"email-remind-24h","sourceHandle":null,"targetHandle":null},{"id":"e-email-remind-24h-x-sms-remind-1h","source":"email-remind-24h","target":"sms-remind-1h","sourceHandle":null,"targetHandle":null},{"id":"e-sms-remind-1h-x-end","source":"sms-remind-1h","target":"end","sourceHandle":null,"targetHandle":null}]}
$graph$)
ON CONFLICT (wf_key) DO UPDATE SET graph_json = EXCLUDED.graph_json, updated_at = now();
