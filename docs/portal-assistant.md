# The Portal assistant

A chat on every recruiter screen that briefs the recruiter on what they have open and explains why
things are as they are. It only reads: it changes nothing, sends nothing and books nothing.

## What it answers about

The widget sends what is open, and the server briefs the most specific of it:

1. an application open in a drawer (scheduling drawer, pipeline drawer);
2. a candidate (the candidate record, the candidates list drawer);
3. a job (the job page, the pipeline for a job, the interview desk filtered to a job);
4. otherwise the day: the interview desk's lanes across every job.

Three asks: **Brief me** (what stands out, then where things stand), **Explain why** (what is waiting,
held back or not sent, the reason the records give, and the next step when the portal has one), and a
question in the recruiter's own words.

## Where each part lives

| Part | Where | What it decides |
|---|---|---|
| Briefing | `domain/briefing/BriefingService` | Which facts are told, what counts as waiting, which reason and next step go with it. The interview desk's lanes come from `SchedulingOverviewService` and the messages from `JourneyQueries`, so the briefing never disagrees with the screens. |
| Voice | `aria/RecruiterAssistant`, `RecruiterBrain` | Puts the briefing into words. `ScriptedRecruiterBrain` lays it out as it is; `ClaudeRecruiterBrain` words it with a model and falls back to the scripted one on any error, refusal, cut-off or empty answer. |
| Endpoint | `api/AssistantController`, `POST /v1/assistant/ask` | Joins the two. 400 for a malformed request, 404 for an id not on file. |
| Widget | `components/PortalAssistant.tsx`, `lib/assistantFocus.ts` | Says what is open and draws the answer. Drawers say what they show with `useAssistantFocus`; every drawer registers itself so the widget steps beside it. |

## Privacy

No name, phone number or email address reaches the model.

- The briefing names every person by a placeholder: `[C1]` for a candidate, `[S1]` for someone on
  the hiring team. Contact details are never copied in, and a candidate's own words (a reply, a
  headline) have anything that looks like an email address or phone number taken out.
- `RecruiterAssistant` replaces known names with their placeholders in the recruiter's question and
  the earlier turns, and puts the names back in the answer.
- The widget sends only the earlier turns about the same thing on screen, so a chat about one
  candidate never travels with a question about another.

## The model

`app.aria.recruiter-model` (`ARIA_RECRUITER_MODEL`, default `claude-opus-5-5`), effort low, a 45
second timeout and one retry. Like the candidate side, it is used only when `app.aria.enabled` is true
and `ANTHROPIC_API_KEY` is set; otherwise every answer is laid out by the rules. Each answer says which
wrote it ("Worded by AI from the records" or "Laid out from the records").

## Not yet

- Answers cover every recruiter's interviews, not only the asker's: the portal has no sign-in yet.
- The careers-chat transcript and contact details are not in the briefing; the answer points to the
  candidate's record for them.
- The chat is kept in the browser only and is gone on reload.
