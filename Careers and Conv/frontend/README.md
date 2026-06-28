# TalentBridge — Frontend

The React + Vite presentation tier. Three surfaces over the deterministic backend — **candidate
runtime**, **recruiter review**, and **admin console** — all driven by the backend's API. No screening
logic lives here; the UI renders bot turns, collects answers, and displays the engine's PASS/FAIL +
reason. The backend ([`../backend`](../backend)) decides everything.

## Stack

- React 18, Vite 5, React Router, **Zustand** for state
- Plain CSS, BofA navy theme

## Run

Backend must be running first (`cd ../backend && ./mvnw spring-boot:run` on :8080).

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173  (Vite proxies /api → :8080)
```

## Routes

| Route | Surface | Notes |
|---|---|---|
| `/` | **Candidate runtime** | Job search / browse → screen → apply → self-schedule |
| `/review` | **Recruiter review** | Pipeline + trail → advance/decline → schedule → comms (token-gated) |
| `/analytics` | **Analytics** | Funnel + conversion, and Fairness (bias audit) tabs (token-gated) |
| `/admin` | **Admin console** | Author flows + rules, guardrail lint, preview, publish (token-gated) |

Header links jump between them. Recruiter/Admin sign-in uses the stub token `dev-admin-token`.
The careers front door (`:8080/careers/...`) “Screen me for a role” launcher opens `/`.

## Candidate runtime (`/`) — §7/§10

- **Landing** — deterministic **job search** by title/skill/location (ranked cards → tap to screen, or
  no-match falls back to all openings), or **browse roles**. FAQ lookup. Optional name. **Voluntary
  EEO self-ID** (clearly marked never-used-for-screening; feeds the aggregate bias audit only).
- **Chat** — bot bubbles, typing indicator, quick-reply buttons / number / text input by `answerType`.
- **Decision trail** — every rule, PASS/FAIL chip, rule id, KNOCKOUT/SCORED, plain-language reason.
- **Completion** — PASSED (green) or NEEDS_REVIEW (amber, "a recruiter will review" — never rejected).
- **Apply** — after completion, "Apply to this position" → confirmation (mock email).
- **Self-schedule** — PASSED candidates pick their own interview slot in chat → booking confirmed
  (mock confirmation + SMS reminder). NEEDS_REVIEW waits for a recruiter.

## Recruiter review (`/review`)

Lists applications (candidate, role, result, knockout flag, status). Drill into one to see the full
screening decision trail, then **Advance to next step** or **Decline** with a note (a human call,
audited). Advanced candidates can be **scheduled** (ranked open slots, preferring the role's
department), and a **Communications timeline** shows every message sent to the candidate.

## Analytics (`/analytics`)

- **Funnel** tab — counts and conversion at each stage (started → completed → passed → applied →
  advanced → booked), top knockout reasons, and a by-role table.
- **Fairness** tab — adverse-impact (four-fifths) audit: per demographic dimension, each group's
  selection (PASS) rate and impact ratio vs. the most-selected group, flagging anything below 0.80.

## Admin console (`/admin`)

- **Flow list** — drafts (editable) vs published (immutable).
- **Flow + node editor** — node copy, branching, add/delete.
- **Rule builder + live guardrail lint** — every edit is linted against the engine; a forbidden
  knockout (e.g. ExperienceYears) surfaces violations inline and **disables Save**.
- **Rule library** — safe rule classes (scored-only ones marked).
- **Preview/simulate** — run the draft as a test candidate via the real engine; nothing is written to
  the pipeline.
- **Publish** (immutable version) and **Export JSON** (§6 contract).

## Layout

```
src/
  api/client.js          public runtime endpoints (incl. self-scheduling)
  api/adminClient.js      token-gated admin/recruiter/analytics endpoints
  store/useSession.js     runtime state (search/screening mode, log, trail, apply, self-schedule, self-ID)
  store/useAdmin.js       admin state (token, flows, detail, rule classes)
  runtime/                RuntimeApp, RolePicker, ChatWindow (+ self-schedule), Composer,
                          DecisionTrail, CompletionBanner, FaqPanel, JobResults
  review/RecruiterReview.jsx   pipeline, advance/decline, scheduling, communications
  review/AnalyticsDashboard.jsx  funnel + fairness (bias audit) tabs
  admin/                  AdminConsole, FlowEditor, RuleEditor, PreviewPanel
  App.jsx                 router (/, /review, /analytics, /admin)
```

## Notes

- All decisions are deterministic and made in the backend — this app has no LLM and no screening logic.
- `RUNTIME_URL` in the careers page points at `http://localhost:5173` for dev; in a deployed build it
  would be the runtime's hosted path.
