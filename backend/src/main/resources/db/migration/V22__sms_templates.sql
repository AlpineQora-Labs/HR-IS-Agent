-- SMS templates for the candidate journey (channel policy: web chat + SMS
-- only). Plain text with the same {{merge_field}} conventions as email.

CREATE TABLE sms_template (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(120) NOT NULL,
    category   VARCHAR(60)  NOT NULL DEFAULT 'Candidate journey',
    body       TEXT         NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO sms_template (id, name, body) VALUES
('70000000-0000-0000-0000-000000000001', 'Application received',
 'Hi {{candidate_name}}, thanks for applying to {{job_title}} at Bank of America. We''ll text you updates here. Reply STOP to opt out.'),
('70000000-0000-0000-0000-000000000002', 'Application status update',
 'Update on your {{job_title}} application: {{status}}. Track it anytime: {{link}}'),
('70000000-0000-0000-0000-000000000003', 'Interview scheduled',
 'Congratulations {{candidate_name}} — your {{interview_type}} is confirmed for {{interview_time}}. Details: {{link}}'),
('70000000-0000-0000-0000-000000000004', 'Interview reminder',
 'Reminder: your interview is {{interview_time}}. Join: {{link}}. Need to change it? Reply RESCHEDULE.'),
('70000000-0000-0000-0000-000000000005', 'Reschedule link',
 'No problem {{candidate_name}} — pick a new time here: {{link}}. Times are shown in your timezone.'),
('70000000-0000-0000-0000-000000000006', 'Identity verified',
 'You''re verified — identity check complete. Next step: schedule your interview at {{link}}');
