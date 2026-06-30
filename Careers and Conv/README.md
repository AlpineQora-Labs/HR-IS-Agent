# TalentBridge

A deterministic conversational hiring app for a regulated bank (internal POC), built on top of an
existing BofA-themed careers site. A Paradox-style conversational hiring platform — discover → screen →
apply → self-schedule → comms — on a fully deterministic, explainable, audited core, with a
best-in-class adverse-impact (bias) audit that conversational competitors don't have.

> **Architecture principle:** *AI at the edge, deterministic at the core.* There is **no LLM anywhere**
> in this system. Screening is a finite-state machine of typed nodes; every rule is a pure function
> returning `{ pass, reason }`; job search, FAQ, scheduling, analytics, and the bias audit are all
> deterministic. Every decision is explainable, audited, and human-reviewed on adverse outcomes.

Full spec: [`CLAUDE.md`](CLAUDE.md) (always-loaded summary) and [`BUILD_SPEC.md`](BUILD_SPEC.md).

## What's here

> **Note:** the live career site is [`bofa-careers-home.html`](bofa-careers-home.html), served by
> [`serve-home.mjs`](serve-home.mjs) on **:5173**, with **Aria's conversational apply running in-page**
> against the recruiting backend (`/v1/chat`). The earlier React/Vite runtime has been archived to
> `_archived-talentbridge-5174/` and is no longer part of the running setup.

| Tier | Module | Stack |
|---|---|---|
| Front door + chat | [`bofa-careers-home.html`](bofa-careers-home.html) — careers home with in-page Aria apply (serve-home.mjs, :5173) | static HTML + vanilla JS → `/v1/chat` |
| _Archived_ | `_archived-talentbridge-5174/` — earlier runtime / recruiter review / analytics / admin console (no longer used) | React + Vite + Zustand |
| Application + Data | [`backend/`](backend) — screening engine, guardrails, audit, versioning, jobs, applications, scheduling, notifications, analytics, bias audit | Spring Boot + JPA, H2/Postgres (Oracle-compatible DDL) |

## Surfaces

- **Candidate** (`/`) — deterministic **job search** → **screen** in chat (live decision trail) →
  **apply** → **self-schedule** an interview (passed candidates) → automated confirmations/reminders.
  Optional, voluntary EEO self-ID (never used to screen).
- **Recruiter** (`/review`) — pipeline + each candidate's screening trail → **advance / decline**
  (human, audited) → **schedule** an interview → **communications** timeline.
- **Analytics** (`/analytics`) — **Funnel** (conversion per stage, top knockout reasons, by role) and
  **Fairness** (four-fifths adverse-impact audit across voluntary demographics).
- **Admin** (`/admin`) — author flows + rules with a **live guardrail**, **preview/simulate** a draft,
  and **publish** an immutable version.

Recruiter / Analytics / Admin are token-gated (POC stub token: `dev-admin-token`).

## Run

**Dev (in-memory H2, zero setup):**
```bash
# terminal 1 — backend (:8080); seeds rule classes, flows, jobs, interviewers + slots
cd backend && ./mvnw spring-boot:run

# terminal 2 — frontend (:5173), proxies /api → :8080
cd frontend && npm install && npm run dev
```

**Real Oracle (persistent — data survives restarts):**
```bash
docker compose up -d                                    # local Oracle 23 Free on :1522
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=oracle
cd frontend && npm run dev                              # unchanged — same API
```
Schema is versioned with **Flyway** (`backend/src/main/resources/db/migration`); the frontend is DB-agnostic.

- **Runtime** — <http://localhost:5173>
- **Recruiter** — <http://localhost:5173/review>
- **Analytics + bias audit** — <http://localhost:5173/analytics>
- **Admin** — <http://localhost:5173/admin>
- **Careers front door** — <http://localhost:8080/careers/bofa-careers-home.html>

Backend tests: `cd backend && ./mvnw test` (26, green).

## Compliance invariants (engine-enforced)

Human-in-the-loop on adverse outcomes (failed knockout → `NEEDS_REVIEW`, never auto-reject) ·
knockouts restricted to safe rule classes · BFOQ justification required · append-only audit ·
immutable published versions · preview/simulate that never touches the real pipeline · recruiter
advance/decline is a human decision · **demographics are voluntary and never enter the decision path**
(which is what makes the four-fifths bias audit trustworthy). See the
[backend README](backend/README.md) for where each is enforced.

## Status

Spec arc P1–P4 plus, beyond the spec: deterministic job search, the apply → recruiter-advance loop,
interview scheduling, **candidate self-scheduling**, automated notifications, a recruiting **funnel
analytics** dashboard, and a best-in-class **adverse-impact bias audit**.

Not built: real SSO, real ATS writes (mock today), omnichannel/multilingual, structured interview
scorecards, identity/fraud checks, talent rediscovery. The screening REST API is the typed surface a
future agent layer (an "HR Concierge") would call as MCP tools — see the agentic-architecture
discussion for how AI plugs in at the edge without touching the deterministic core.
