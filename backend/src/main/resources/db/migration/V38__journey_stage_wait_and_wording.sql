-- V38: a stage is told after a short wait, and the journey's own "application received"
-- email stops promising texts to people who may not get them.
--
-- 1. journey_stage_reached. A move to ASSESSMENT, OFFER, HIRED or REJECTED is noted here, and the
--    journey's sweep tells the candidate once the move is a few minutes old
--    (app.journey.stage-grace-minutes), and only if the application is still at that stage. The
--    pipeline moves one stage at a time, so a candidate taken from Interview to Offer passes
--    Assessment on the way; a stage only passed through is gone by the time the sweep looks and is
--    not told. from_stage is the stage left: a candidate who was offered the job or hired is never
--    told by template that they were not taken forward.
CREATE TABLE IF NOT EXISTS journey_stage_reached (
    application_id UUID        NOT NULL REFERENCES application (id) ON DELETE CASCADE,
    point          VARCHAR(40) NOT NULL,
    from_stage     VARCHAR(40),
    reached_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (application_id, point)
);
-- The sweep reads the rows whose wait is over, oldest first.
CREATE INDEX IF NOT EXISTS ix_journey_stage_reached_at ON journey_stage_reached (reached_at);
COMMENT ON TABLE journey_stage_reached IS 'Stages reached and not yet told to the candidate; the journey sweep tells them after a short wait (StagesReached)';

-- 2. The journey's own email for "When the candidate applies" (V24) told every applicant to reply
--    STATUS to a text and promised times by text, though not everyone agrees to texts. Said only
--    for those who get texts now. replace() leaves the wording alone where an administrator has
--    already changed these sentences.
UPDATE email_template
SET body_html = replace(replace(body_html,
        '<li>You can check where your application stands at any time by replying STATUS to our text message.</li>',
        '<li>If you receive texts from us, you can reply STATUS at any time to see where your application stands.</li>'),
        'we will text and email you times to choose from',
        'we will send you times to choose from'),
    updated_at = now()
WHERE template_key = 'journey.application_received'
  AND (body_html LIKE '%by replying STATUS to our text message%'
       OR body_html LIKE '%we will text and email you times to choose from%');
