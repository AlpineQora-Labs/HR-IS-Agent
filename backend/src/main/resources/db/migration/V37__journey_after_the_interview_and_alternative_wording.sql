-- Candidate journey: what is said after the interview, and other wordings to choose from.
--
--  * Four new points of the journey (JourneyPoint): moved to assessment, moved
--    to offer, hired, and not taken forward. Each has its own text and email,
--    keyed journey.<point> like every other point. They go out only once an
--    administrator draws a step for them on the workflow canvas, so adding them
--    here sends nothing by itself.
--  * Alternative wordings for the points already on the drawing, with no
--    journey key. Each carries every field its point needs and only fields its
--    point has values for, so the canvas offers it as a choice for that step.
--
--  * A template can now be offered for one step in particular (journey_point):
--    the canvas then lists it for that step and no other. Without it, a short
--    wording that only uses the candidate's name and the job would be offered
--    for every step those two fields fit, from applying to being hired.
--
-- Every text keeps to the characters every phone shows: no curly quotes, long
-- dashes or emoji. Nothing here promises what the system does not do.

ALTER TABLE sms_template ADD COLUMN IF NOT EXISTS journey_point VARCHAR(40);
ALTER TABLE email_template ADD COLUMN IF NOT EXISTS journey_point VARCHAR(40);
COMMENT ON COLUMN sms_template.journey_point IS 'The step of the workflow drawing this template is offered for (a JourneyPoint name); null for any step its fields fit';
COMMENT ON COLUMN email_template.journey_point IS 'The step of the workflow drawing this template is offered for (a JourneyPoint name); null for any step its fields fit';

INSERT INTO sms_template (id, template_key, name, body, category, status)
SELECT v.id::uuid, v.template_key, v.name, v.body, 'Candidate journey', 'ACTIVE'
FROM (VALUES
    ('71000000-0000-0000-0000-000000000026', 'journey.assessment_invite', 'Moved to assessment',
     '{{company_name}}: Good news, {{first_name}}. Your application for {{job_title}} moves on to an assessment. You will hear from {{recruiter_name}} with the details.'),
    ('71000000-0000-0000-0000-000000000027', 'journey.offer_extended', 'Moved to offer',
     '{{company_name}}: Good news, {{first_name}}. An offer for {{job_title}} is being prepared, and you will hear from {{recruiter_name}} with the details.'),
    ('71000000-0000-0000-0000-000000000028', 'journey.hired', 'Hired',
     '{{company_name}}: Welcome to the team, {{first_name}}. You will hear from {{recruiter_name}} about your start date and your first day as {{job_title}}.'),
    ('71000000-0000-0000-0000-000000000029', 'journey.not_selected', 'Not taken forward',
     '{{company_name}}: {{first_name}}, thank you for applying for {{job_title}}. We will not be moving forward with your application this time. We wish you well in your search.')
) AS v (id, template_key, name, body)
WHERE NOT EXISTS (SELECT 1 FROM sms_template t WHERE t.template_key = v.template_key);

INSERT INTO email_template (id, template_key, name, description, category, subject, from_name, body_html, status)
SELECT v.id::uuid, v.template_key, v.name, v.description, v.category, v.subject, 'Bank of America Careers', v.body_html, 'ACTIVE'
FROM (VALUES
    ('98100000-0000-0000-0000-000000000007', 'journey.assessment_invite', 'Moved to assessment',
     'Sent when an application moves to assessment', 'INVITATION',
     'Your next step for {{job_title}}: an assessment',
     '<h2>Your next step, {{first_name}}</h2><p>Your application for <strong>{{job_title}}</strong> moves on to an assessment.</p><p><strong>What happens next</strong></p><ul><li>You will hear from {{recruiter_name}} with the assessment: what it covers, how to take it and by when.</li><li>If you receive texts from us, you can reply STATUS at any time to see where your application stands.</li></ul><p>Regards,<br>{{sender_name}}</p>'),
    ('98100000-0000-0000-0000-000000000008', 'journey.offer_extended', 'Moved to offer',
     'Sent when an application moves to offer', 'ANNOUNCEMENT',
     'Your {{job_title}} application: an offer is being prepared',
     '<h2>Good news, {{first_name}}</h2><p>We are preparing an offer for <strong>{{job_title}}</strong>.</p><p><strong>What happens next</strong></p><ul><li>You will hear from {{recruiter_name}} with the written offer and how to accept it.</li><li>The written offer sets out the terms and any conditions.</li></ul><p>Regards,<br>{{sender_name}}</p>'),
    ('98100000-0000-0000-0000-000000000009', 'journey.hired', 'Hired',
     'Sent when a candidate is hired', 'WELCOME',
     'Welcome to the team, {{first_name}}',
     '<h2>Welcome to the team, {{first_name}}</h2><p>We are glad you are joining us as <strong>{{job_title}}</strong>.</p><p><strong>What happens next</strong></p><ul><li>You will hear from {{recruiter_name}} about your start date and your first day.</li><li>Before you start, you will receive what you need for onboarding, including anything we need from you.</li></ul><p>Regards,<br>{{sender_name}}</p>'),
    ('98100000-0000-0000-0000-000000000010', 'journey.not_selected', 'Not taken forward',
     'Sent when an application is not taken forward', 'THANK_YOU',
     'Your application for {{job_title}}',
     '<h2>Thank you, {{first_name}}</h2><p>Thank you for your interest in <strong>{{job_title}}</strong> and for taking the time to apply.</p><p>We have decided not to move forward with your application for this role.</p><p>Our other openings are on our careers site, and you are welcome to apply for any role that interests you.</p><p>Regards,<br>{{sender_name}}</p>')
) AS v (id, template_key, name, description, category, subject, body_html)
WHERE NOT EXISTS (SELECT 1 FROM email_template t WHERE t.template_key = v.template_key);

-- Other wordings for the points already on the drawing. No journey key: the
-- canvas lists them beside the point's own wording, and a step can choose one.
INSERT INTO sms_template (id, template_key, name, body, category, status)
SELECT v.id::uuid, NULL, v.name, v.body, 'Candidate journey', 'ACTIVE'
FROM (VALUES
    ('72000000-0000-0000-0000-000000000001', 'Application received, short',
     '{{company_name}}: Thank you, {{first_name}}. Your application for {{job_title}} is in. Reply STATUS for an update.'),
    ('72000000-0000-0000-0000-000000000002', 'Status update, with help',
     '{{company_name}}: Hi {{first_name}}. {{status_summary}} Reply HELP for what else you can do by text.'),
    ('72000000-0000-0000-0000-000000000003', 'Interview invitation, with the link',
     E'{{company_name}}: {{first_name}}, we would like to interview you for {{job_title}}. Reply {{slot_choices}} to pick a time:\n{{slot_options}}\nOr choose online: {{link}}'),
    ('72000000-0000-0000-0000-000000000004', 'Interview confirmed, short',
     E'{{company_name}}: Confirmed, {{first_name}}. Your {{job_title}} interview is {{interview_time}}. Join: {{meeting_link}}\nReply RESCHEDULE to change it.'),
    ('72000000-0000-0000-0000-000000000005', 'New times after a reschedule, with the link',
     E'{{company_name}}: Your earlier time is released, {{first_name}}. Reply {{slot_choices}} to pick a new time:\n{{slot_options}}\nOr choose online: {{link}}'),
    ('72000000-0000-0000-0000-000000000006', 'Reminder, 24 hours before, with the panel',
     E'{{company_name}}: Coming up, {{first_name}}: your {{interview_type}} for {{job_title}}, {{interview_time}}, with {{interviewers}}. Join: {{meeting_link}}\nReply RESCHEDULE to change it.'),
    ('72000000-0000-0000-0000-000000000007', 'Reminder, 1 hour before, short',
     '{{company_name}}: {{first_name}}, your interview is {{interview_time}}. Join: {{meeting_link}}')
) AS v (id, name, body)
WHERE NOT EXISTS (SELECT 1 FROM sms_template t WHERE t.id = v.id::uuid);

INSERT INTO email_template (id, template_key, name, description, category, subject, from_name, body_html, status)
SELECT v.id::uuid, NULL, v.name, v.description, v.category, v.subject, 'Bank of America Careers', v.body_html, 'ACTIVE'
FROM (VALUES
    ('98200000-0000-0000-0000-000000000001', 'Application received, what happens next',
     'Another wording for when a candidate applies', 'WELCOME',
     'Thank you for applying, {{first_name}}',
     '<h2>Thank you for applying, {{first_name}}</h2><p>We received your application for <strong>{{job_title}}</strong>. Our recruiting team reviews every application.</p><p><strong>What happens next</strong></p><ul><li>If you are selected for interview, we will send you times to choose from.</li><li>If you receive texts from us, you can reply STATUS at any time to see where your application stands.</li></ul><p>Regards,<br>{{sender_name}}</p>'),
    ('98200000-0000-0000-0000-000000000002', 'Interview invitation, short',
     'Another wording for when a candidate is selected for interview', 'INVITATION',
     'Interview times for {{job_title}}',
     '<p>Hi {{first_name}},</p><p>We would like to interview you for <strong>{{job_title}}</strong>. Please choose one of these times (Eastern Time):</p><p>{{slot_options}}</p><p><a class="email-cta-button" href="{{link}}" target="_blank">Choose a time</a></p><p>Regards,<br>{{sender_name}}</p>'),
    ('98200000-0000-0000-0000-000000000003', 'Interview confirmed, with how to prepare',
     'Another wording for when an interview is booked', 'INVITATION',
     'Confirmed: {{job_title}} interview, {{interview_time}}',
     '<h2>Your interview is confirmed</h2><p>Hi {{first_name}}, here are the details.</p><p><strong>Role:</strong> {{job_title}}<br><strong>When:</strong> {{interview_time}}<br><strong>Format:</strong> {{interview_type}}<br><strong>With:</strong> {{interviewers}}</p><p><a class="email-cta-button" href="{{meeting_link}}" target="_blank">Join the interview</a></p><p><strong>Before the interview</strong></p><ul><li>If your interview is by video, test the link a little ahead of time, on the device you will use.</li><li>Have your resume to hand, and a few questions for the team.</li><li>If the time no longer works, <a href="{{link}}" target="_blank">choose a new time</a>.</li></ul><p>Regards,<br>{{sender_name}}</p>'),
    ('98200000-0000-0000-0000-000000000004', 'Reminder, with how to prepare',
     'Another wording for 24 hours before the interview', 'REMINDER',
     'Coming up: your {{job_title}} interview, {{interview_time}}',
     '<h2>Your interview is coming up</h2><p>Hi {{first_name}}, a reminder of your interview.</p><p><strong>Role:</strong> {{job_title}}<br><strong>When:</strong> {{interview_time}}<br><strong>With:</strong> {{interviewers}}</p><p><a class="email-cta-button" href="{{meeting_link}}" target="_blank">Join the interview</a></p><p><strong>A few minutes before</strong></p><ul><li>If your interview is by video, open the link a little early to check your sound and camera.</li><li>Have your resume to hand.</li></ul><p>If the time no longer works, <a href="{{link}}" target="_blank">choose a new time</a>.</p><p>Regards,<br>{{sender_name}}</p>')
) AS v (id, name, description, category, subject, body_html)
WHERE NOT EXISTS (SELECT 1 FROM email_template t WHERE t.id = v.id::uuid);

-- Each other wording is offered for its own step only. Set separately from the
-- inserts so that it also lands on rows already there.
UPDATE sms_template t SET journey_point = v.point
FROM (VALUES
    ('72000000-0000-0000-0000-000000000001', 'APPLICATION_RECEIVED'),
    ('72000000-0000-0000-0000-000000000002', 'STATUS_REPLY'),
    ('72000000-0000-0000-0000-000000000003', 'INTERVIEW_INVITE'),
    ('72000000-0000-0000-0000-000000000004', 'INTERVIEW_CONFIRMED'),
    ('72000000-0000-0000-0000-000000000005', 'RESCHEDULE_OPTIONS'),
    ('72000000-0000-0000-0000-000000000006', 'INTERVIEW_REMINDER_24H'),
    ('72000000-0000-0000-0000-000000000007', 'INTERVIEW_REMINDER_1H')
) AS v (id, point)
WHERE t.id = v.id::uuid AND t.journey_point IS NULL;

UPDATE email_template t SET journey_point = v.point
FROM (VALUES
    ('98200000-0000-0000-0000-000000000001', 'APPLICATION_RECEIVED'),
    ('98200000-0000-0000-0000-000000000002', 'INTERVIEW_INVITE'),
    ('98200000-0000-0000-0000-000000000003', 'INTERVIEW_CONFIRMED'),
    ('98200000-0000-0000-0000-000000000004', 'INTERVIEW_REMINDER_24H')
) AS v (id, point)
WHERE t.id = v.id::uuid AND t.journey_point IS NULL;
