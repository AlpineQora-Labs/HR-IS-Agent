-- The assistant goes by Erica wherever a candidate or a recruiter reads its
-- name (app.assistant-name). Text stored before that still said Aria, and the
-- oldest seed rows said Olivia. Only the assistant's own words about itself
-- change: the introductions it opens with, its label as an interviewer, and
-- its name as an actor in the audit trail. A person named Aria keeps their
-- name, in what they typed and in the assistant's replies to them (V17 did
-- the same for people named Olivia).

UPDATE message SET body = regexp_replace(body, '\mAria\M', 'Erica', 'g')
 WHERE sender = 'ARIA'
   AND intent IN ('GREETING', 'evreg.greeting', 'apply.start', 'voice.intro')
   AND body ~ '\mAria\M';
UPDATE interview SET interviewers = replace(interviewers, 'Aria (AI)', 'Erica (AI)')
 WHERE interviewers LIKE '%Aria (AI)%';
UPDATE audit_event SET actor = 'Erica' WHERE actor IN ('olivia', 'aria');
