# TalentBridge — Conversational Screening App

A deterministic conversational hiring app for a regulated bank (internal POC). Sits on top of an
existing BofA-themed careers site. Two things to build: a **chat runtime** that executes
admin-authored screening flows, and an **admin console** to author the flow text and the rules.

Full detail lives in `BUILD_SPEC.md`. Read it before building. This file is the always-loaded summary.

## Stack
- Frontend: React + Vite, React Router, Zustand for state.
- Backend: Spring Boot (REST), service layer, JPA/Hibernate.
- DB: Postgres for dev (H2 acceptable to bootstrap). Keep schema Oracle-compatible.
- Auth: stubbed admin login for the POC; real SSO is out of scope.

## Commands
- Frontend: `cd frontend && npm install && npm run dev`
- Backend: `cd backend && ./mvnw spring-boot:run`
- The existing careers site (`bofa-careers-home.html`) is the front door — serve it statically or
  port it into React. The chat launcher in it opens the runtime.

## Architecture principle (do not violate)
**AI at the edge, deterministic at the core.** The screening engine is fully deterministic and
explainable. No LLM is in the screening decision path for this POC. Conversation is a finite-state
flow of typed nodes; every rule evaluation is a pure function producing a logged PASS/FAIL + reason.

## Compliance invariants (enforced by the engine, never left to a rule author)
1. **Human-in-the-loop on adverse outcomes.** A failed/knockout screening sets status
   `NEEDS_REVIEW` — never auto-reject. A recruiter makes the final call.
2. **Knockouts only for safe rule classes** (WorkAuth, License/Cert, BackgroundCheckConsent,
   Location/Onsite, hard Availability). The engine must WARN or BLOCK any attempt to mark
   experience-years, salary, or soft-skills as a knockout.
3. **BFOQ justification required** to save any knockout rule (free-text, stored).
4. **Append-only audit** of every prompt, answer, rule eval, and decision (timestamp, actor,
   trace id). Never mutate or delete audit rows.
5. **Published versions are immutable.** The runtime executes a published flow/rule version, not a draft.
6. **Preview/simulate** must exist in admin (take the flow as a test candidate before publishing).

## Conventions
- Rules and flows are authored in the DB and exportable/importable as JSON (see BUILD_SPEC §6).
  The export JSON is the contract a future shared ScreeningService consumes — keep it stable.
- Keep services thin and testable; one service per domain (ScreeningService, FlowService,
  AuditService, CandidateService).
- Seed data: reuse the starter roles/criteria in BUILD_SPEC §15.

## Non-goals (this POC)
LLM in the decision path; interview scheduling (phase 2); real ATS writes (use a mock adapter);
real SSO; production hardening.
