-- Candidate journey: two more points, and their wording.
--
--  * When numbers cannot be used in reply (another invitation is open to the
--    same phone, or the phone is shared by two people), a moved interview is
--    announced with a link. It used to go out worded as a new invitation.
--  * A text from a number more than one person gave is answered without
--    saying anything about any of them.

INSERT INTO sms_template (id, template_key, name, body, category, status)
SELECT v.id::uuid, v.template_key, v.name, v.body, 'Candidate journey', 'ACTIVE'
FROM (VALUES
    ('71000000-0000-0000-0000-000000000024', 'journey.team_rescheduled_by_link', 'Interview moved by the hiring team, by link',
     '{{company_name}}: {{first_name}}, the hiring team has to move your {{job_title}} interview, and we are sorry for the change. Choose a new time here: {{link}}'),
    ('71000000-0000-0000-0000-000000000025', 'journey.shared_number', 'Number given by more than one person',
     '{{company_name}}: This number is on more than one application, so we cannot give details or make changes by text. Please use the links in our emails to you.')
) AS v (id, template_key, name, body)
WHERE NOT EXISTS (SELECT 1 FROM sms_template t WHERE t.template_key = v.template_key);

-- The invitation by link also goes to a shared phone, where there is no other
-- invitation for "also" to refer to. A template somebody has edited is left alone.
UPDATE sms_template
   SET body = '{{company_name}}: Good news, {{first_name}}. We would like to interview you for {{job_title}}. Choose a time here: {{link}}',
       updated_at = now()
 WHERE template_key = 'journey.invite_by_link'
   AND body = '{{company_name}}: Good news, {{first_name}}. We would also like to interview you for {{job_title}}. Choose a time here: {{link}}';
