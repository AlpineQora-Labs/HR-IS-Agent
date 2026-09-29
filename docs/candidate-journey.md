# Candidate journey — text and email

Status: built (2026-09-29). Owner: TA Portal. Sending is simulated until a provider is connected (section 8).

The journey a candidate goes through after applying, carried by **text message** and **email**.
Channels are web chat and SMS for conversation, email for notices. WhatsApp is never used.

## 1. The journey

| # | Point (key) | What happens | Text | Email |
|---|---|---|---|---|
| 1 | `APPLICATION_RECEIVED` | The candidate applies and passes screening. | yes | yes |
| 2 | `STATUS_REPLY` | The candidate texts STATUS. They get where each of their applications stands (up to three), in plain words. | reply | – |
| 3 | `INTERVIEW_INVITE` | The candidate is selected for interview (application moved to INTERVIEW, or the team suggests times). Three times are taken from the hiring manager's and interviewers' calendars and offered. | yes | yes |
| 4 | `INTERVIEW_CONFIRMED` | The candidate replies 1, 2 or 3 (or books on the web page, or in chat). The interview is booked. | yes | yes |
| 5 | `RESCHEDULE_OPTIONS` | The candidate texts RESCHEDULE. Policy is checked, calendars are read again, new times are offered. | reply | – |
| 6 | `INTERVIEW_REMINDER_24H` | The day before the interview. | yes | yes |
| 7 | `INTERVIEW_REMINDER_1H` | The last hour before the interview. | yes | – |

These seven are on the drawing. Two further groups are not drawn; their wording is edited in Communications.

**Notices** — sent because the hiring team did something the candidate must hear of:

| Key | When | Text | Email |
|---|---|---|---|
| `TEAM_RESCHEDULED` | The hiring team moves a booked interview; new times are offered. | yes | yes |
| `INTERVIEW_CANCELED` | The hiring team cancels, or the application is rejected or withdrawn. | yes | yes |
| `INVITE_BY_LINK` | An invitation goes out when numbers cannot be used in reply: another invitation is open to the same phone, or the phone was given by two people. It carries a link. | yes | – |
| `TEAM_RESCHEDULED_BY_LINK` | The same, for an interview the hiring team moved. | yes | – |

**Service replies** — what the system answers when a candidate texts:

| Key | When |
|---|---|
| `MORE_TIMES` | MORE: later times are offered. |
| `NO_OTHER_TIMES` | MORE, and there is nothing later; the times offered stand. |
| `SLOT_TAKEN` | The time picked was taken a moment ago; new times follow. |
| `NO_TIMES_AVAILABLE` | Calendars are full. The hiring team is alerted; the candidate is told times will follow, and they do when calendars open. |
| `RESCHEDULE_DECLINED` | The reschedule limit is used up or the interview is too close. The recruiter is told. |
| `RESCHEDULE_NO_TIMES` | No other time is open: the booking is kept, and the recruiter is told. |
| `NOTHING_TO_RESCHEDULE` | RESCHEDULE with no interview booked or on offer. |
| `ALREADY_BOOKED` | A number is sent when the interview is already booked. |
| `PICK_A_NUMBER` | Times are on offer and the reply was not one of them; the times are restated. |
| `HELP` | HELP, or anything not understood. |
| `OPT_OUT` / `OPT_IN` | STOP / START. |
| `UNKNOWN_SENDER` | A text from a number we have no candidate for (answered once a day). |
| `SHARED_NUMBER` | A text from a number more than one person gave (answered once a day; recruiters are told). |

Keywords (case-insensitive): `STATUS`, `1` `2` `3`, `MORE`, `TIMES`, `RESCHEDULE`, `HELP`, `STOP`, `START`.

- A number books a time **only** when it is the whole message ("2", "option 2", "#2", "2 please"). "Can we do 2pm" books nothing.
- `STOP`, `STOPALL`, `UNSUBSCRIBE`, `CANCEL`, `END`, `QUIT`, `REVOKE` and "opt out" as the whole message are opt-out, as carriers require.
  So is opting out said in a sentence: "please stop texting me", "do not text me", "unsubscribe me", "remove me".
- **Where a text can be read two ways, the reading that changes nothing wins.** RESCHEDULE moves an interview only when it
  is asked for plainly ("reschedule", "I need to reschedule", "can we reschedule?"). A sentence that may be asking
  ("I can't make it", "need a different time") moves nothing: the candidate is told what they are booked for and to reply
  RESCHEDULE. "Thanks for rescheduling" and "no need to reschedule" ask for nothing.
- A reply is about what was last texted. With times on offer, RESCHEDULE asks for other times and leaves a booking for
  another role alone. With more than one interview booked, none is moved: the candidate is pointed to the links in their
  emails and the recruiter is told.
- HELP asked for by name is always answered. Texts nobody understood are answered three times in ten minutes,
  then met with silence, so two machines cannot talk all night.

## 2. Rules (all enforced in the Java service layer)

1. **Consent.** A text is sent only when the number is a valid mobile number, the candidate agreed to be texted
   (`sms_consent_at`), and the number has not opted out. A number given with "please don't text me" is kept and is not
   agreement. Otherwise the message is recorded as not sent, with the reason,
   and the recruiter is told if the candidate was reached on no channel at all. Email is unaffected by STOP.
2. **STOP belongs to the phone number**, not the candidate record (`sms_opt_out`). After STOP only the opt-out confirmation
   and the answers to HELP and START are texted; anything else received is recorded and not acted on. STOP also closes any
   times on offer by text; the link in the email still works.
3. **The first text to a number** carries the opt-out and rates wording if the template does not.
4. **Three times, not overlapping.** `scheduling_policy.proposal_count` (default 3, 1 to 6). Times offered on one day are
   at least two hours apart.
5. **What was offered is what is booked.** The numbered times are stored with the message as *times*. "2" means the second
   time in the last offer sent. If that time is gone, the candidate gets `SLOT_TAKEN` and a new list. An offer is acted on
   once, however many replies arrive together.
6. **Booking is guarded.** The interview and the panel are locked while a time is taken; a unique index on the calendar
   (`uq_calendar_interview_start`) makes a double booking impossible even if the code is wrong. An application has one
   interview booked at a time. A time the hiring team books directly is held to the same checks, blocks the interviewers'
   calendars, and withdraws whatever was on offer to the candidate.
7. **Reschedule by text is policied** (`reschedule_limit`, `reschedule_cutoff_hours`) — the same rule the self-schedule
   page uses. The policy is checked *before* anything is released, and the booking is kept unless other times exist.
   The hiring team is held to the second half too: it cannot move an interview when no other time is open.
8. **Reminders and confirmations belong to a booking** — the interview, the time, and when it was booked — so a time
   given up and booked again is confirmed and reminded of again, and an interview moved twice is announced twice.
   A reminder held back (no agreement, STOP) is recorded once and goes if the candidate may be texted later.
   The 24-hour reminder is sent between 24 and 12 hours before, and only when the interview was booked more than
   24 hours ahead; booked later than that, the confirmation is the reminder. The 1-hour reminder is sent in the last hour.
   Reminders are sent by the journey only — the chat assistant no longer sends its own.
9. **Status wording** is one mapping from stage (and interview state) to a sentence. The screen never invents wording.
10. **Nothing is sent from inside a business transaction.** Services publish an event; the journey hears it after commit.
    A booking is never rolled back because a message failed, and a failure raises an alert for administrators.
11. **Every message is recorded** — what, to whom, which channel, which template, status — before it is handed to a gateway.
    A message is handed over once (claimed `QUEUED` to `SENDING`); anything recorded and never sent is swept up.
12. **Journey templates cannot be deleted or switched off**, a text template must keep the fields its point needs
    (an invitation without `{{slot_options}}` is refused), and no journey template may use a field its point has no value
    for (an opt-out confirmation cannot name a role), so a journey point always has wording that can be sent.
13. **A phone number two people gave is nobody's in particular.** Records that share a number are one person when the
    email or the name is the same. Otherwise a text from the number says and changes nothing about either, invitations
    go with a link, each record shows only its own messages, and the recruiters are told.

## 3. What the drawing controls

The enabled workflow with trigger **Candidate journey** is the journey's configuration:

- A **Text** or **Email** step bound to a journey point ("Sent when…") decides *which template* goes out at that point.
- For the points sent unprompted (1, 3, 4, 6, 7), the step being on the drawing decides *whether that channel is used*.
  Application received by text only: remove the Email step. Both: keep both.
- Switching the workflow off stops unprompted messages and notices. Replies to a candidate's own text are always given.
- A step counts when a line leads to it from the start. A step cut out of the flow sends nothing, and the check of the
  drawing says so.
- A journey is not routed like a request, so it has no Test.
- The *order of events* is not run from the drawing: events come from the candidate and the hiring team.
  The drawing shows the path; the system follows the events.

Which templates can word a point is decided by the server (`JourneyTemplates`): Active, not the wording of another
point, carrying what the point needs, and using nothing the point has no value for. The canvas lists exactly those.
A step with no template chosen, or one whose template is not fit, falls back to the journey's own template for that
point. Saving the drawing gives advice when a message step is not bound to a point, is bound to a point its channel
cannot carry, repeats a point, or names a template that is not fit.

## 4. Architecture

Dependency rule (docs/aria-module.md) is kept: `aria -> domain`, `api -> aria`, `domain` never imports `aria`.

```
api        CandidateMessageController  GET  /v1/candidates/{id}/messages         timeline
                                       POST /v1/candidates/{id}/messages/reply   play the candidate's phone
                                       POST /v1/sms/inbound                      provider webhook shape
           JourneyController           GET  /v1/journey                          points, merge fields, rules
                                       POST /v1/interviews/{id}/reminders/{24h|1h}   send a reminder now

aria.sms   SmsConversation           one inbound text -> intent -> journey calls -> replies
           SmsIntentParser           pure: text -> intent

domain.journey    JourneyPoint       the points, their channels, needed fields and template keys
                  JourneyPlan        reads the drawn journey: point -> template per channel; advice on saving
                  JourneyTemplates   the wording for a point: the step's template, else the journey's own
                  JourneyValues      the merge-field values for a candidate, application and interview
                  JourneyUnits       each thing the journey does, in a transaction of its own
                  JourneyService     the facade: runs units, then hands messages to the dispatcher; never throws
                  JourneyListener    after-commit listener for domain events
                  JourneyReminderJob scheduled: reminders, promised times, unsent messages
                  JourneyQueries     read side for the screens
                  StatusWording      pure: stage + interview -> sentence
                  JourneyTimes       pure: a time as the candidate reads it ("Tue, Oct 6, 10:00 AM ET")

domain.messaging  CandidateMessage   table candidate_message: the record of every text and email, in and out
                  MessagePolicy      may this go: consent, opt-out, a usable address
                  MessageStore       writes the record
                  MessageDispatcher  hands QUEUED records to a gateway outside any transaction; marks SENT / FAILED
                  SmsGateway / EmailGateway   interfaces; Simulated* implementations today
                  TemplateRenderer   pure: {{merge_field}} -> text; reports what it could not fill
                  SmsText            pure: text a phone can show in the plain alphabet; counts parts
                  PhoneNumbers       pure: normalise to E.164

domain events     ApplicationReceived, ApplicationStageChanged, InterviewTimesOffered (PROPOSED | MOVED),
                  InterviewBooked (origin TEXT | WEB_PAGE | CHAT | TEAM), InterviewCanceled
                  published by the services that make the change; heard after commit
```

## 5. Data (migrations V24 to V26)

- `candidate`: `phone_e164` (CHECK E.164, indexed), `sms_consent_at`.
- `sms_opt_out`: the numbers that said STOP.
- `scheduling_policy.proposal_count` (CHECK 1..6, default 3). `interview.booked_at`.
- `calendar_event`: unique (user, start) for interviews.
- `sms_template.template_key`, `email_template.template_key` (unique when set).
- `candidate_message`: candidate, application, interview, channel (`SMS`|`EMAIL`), direction (`OUTBOUND`|`INBOUND`),
  address, subject, body, journey point, template, status (`QUEUED`|`SENDING`|`SENT`|`FAILED`|`SUPPRESSED`|`RECEIVED`),
  reason, provider, provider message id (unique per provider), dedupe key (unique), offer (the numbered times) and when
  it closed, timestamps.
- 25 text templates and 6 email templates for the journey (V24, V26; wording corrections in V25). Older starter
  templates that nobody had edited are archived.
- The Candidate journey workflow, redrawn. The drawing it replaces is kept, switched off, as
  "Candidate journey (earlier drawing)".

## 6. Merge fields (one vocabulary for text and email)

`candidate_name`, `first_name`, `job_title`, `company_name`, `status_summary`, `slot_options`, `interview_time`,
`interview_type`, `interviewers`, `meeting_link`, `link` (self-schedule page), `reason`, `recruiter_name`, `sender_name`.

Older names still work: `participant_name` = `candidate_name`, `register_link` = `link`, `status` = `status_summary`.
Times are Eastern Time and say so. The screens read the vocabulary from `GET /v1/journey`.

## 7. Screens

- **Messages** on a candidate (record page, candidates drawer): every text and email in order, with channel, the point
  that caused it, whether it was sent, and why not when it was not.
- **Candidate's phone**: a phone to text from as the candidate, on the candidate record and on its own at
  `/phone/{candidateId}`, for showing the journey without a handset.
- **Workflow canvas**: a Text or Email step has "Sent when", which binds it to a journey point.
- **Communications**: journey templates show the point they word; merge fields come from the server.

## 8. Not in this build

- A real SMS or email provider. It is one class implementing `SmsGateway` or `EmailGateway`, chosen by
  `app.messaging.sms-provider` / `email-provider`. Its credentials are entered by the owner of the account and are
  never stored in the repository.
- Candidate time zones (everything is Eastern Time).
- Text in languages other than English.
- Cancelling an interview by text.
- Web chat's own reschedule still uses the unpolicied path (known, tracked in the scheduler scenarios).
