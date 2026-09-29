-- Candidate journey: wording corrections to the templates V24 seeded.
-- A template somebody has edited since is left as they wrote it.

-- "is at Thu, Oct 1, 10:30 AM ET" does not read; "starts Thu, Oct 1, 10:30 AM ET" does.
UPDATE sms_template
   SET body = '{{company_name}}: Starting soon, {{first_name}}. Your {{job_title}} interview starts {{interview_time}}. Join: {{meeting_link}}',
       updated_at = now()
 WHERE template_key = 'journey.interview_reminder_1h'
   AND body = '{{company_name}}: Starting soon, {{first_name}}. Your {{job_title}} interview is at {{interview_time}}. Join: {{meeting_link}}';
