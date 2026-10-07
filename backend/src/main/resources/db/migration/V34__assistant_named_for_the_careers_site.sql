-- The assistant no longer goes by Erica: on every screen and in every message it is
-- Bank of America Careers, the name the text messages already carry (app.assistant-name).
-- Only the assistant's own words and labels change; what a person typed is left alone.

UPDATE message SET body = regexp_replace(regexp_replace(body, 'I(''|’)m Erica, the careers assistant', 'this is Bank of America Careers', 'g'), 'I(''|’)m Erica', 'this is Bank of America Careers', 'g')
 WHERE sender = 'ARIA' AND body ~ '\mErica\M';
UPDATE message SET body = regexp_replace(body, '\mErica\M', 'Bank of America Careers', 'g')
 WHERE sender = 'ARIA' AND body ~ '\mErica\M';
UPDATE interview SET interviewers = replace(interviewers, 'Erica (AI)', 'Bank of America Careers (AI)')
 WHERE interviewers LIKE '%Erica (AI)%';
UPDATE audit_event SET actor = 'Bank of America Careers' WHERE actor = 'Erica';
