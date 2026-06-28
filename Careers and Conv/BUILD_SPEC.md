# TalentBridge — Build Spec

Conversational screening app for an internal bank POC. This spec is the source of truth; `CLAUDE.md`
is the short version that loads each session.

---

## 1. Scope

On top of an **existing careers site** (`bofa-careers-home.html`, already built, BofA-themed, with a
collapsed chat launcher), build:

1. **Chat runtime** — executes admin-authored deterministic screening flows for a candidate.
2. **Admin console** — author the flow (what the bot says) and the rules (what the bot decides),
   preview/simulate, publish versions, and review the audit + candidate pipeline.

The careers site is the front door. Its chat launcher opens the runtime. Do not rebuild the careers
site; integrate it (serve static, or port the markup into a React route).

---

## 2. Principles

- **AI at the edge, deterministic at the core.** No LLM decides screening outcomes in this POC. The
  flow is a finite-state machine of typed nodes; each rule is a pure function returning
  `{ pass: boolean, reason: string }`.
- **Explainable.** Every decision shows the rule that fired, the candidate answer, and the reason.
- **Auditable.** Every step is written to an append-only log.
- **Guardrails in the engine, not in the author's hands** (see §8).

---

## 3. Stack

- **Frontend:** React + Vite, React Router, Zustand. Component-per-view; shared API client.
- **Backend:** Spring Boot, REST controllers → services → JPA repositories → DB.
- **DB:** Postgres (dev). H2 acceptable to bootstrap fast. Keep DDL Oracle-compatible.
- **Auth:** stub admin login (hardcoded/dev) for POC. Real SSO out of scope.

---

## 4. Domain model

```
flow            (id, name, status[DRAFT|PUBLISHED|ARCHIVED], version, created_at, published_at)
flow_node       (id, flow_id, type, order_index, prompt_text, quick_replies(json),
                 fallback_text, transition_text, next_node_id, branches(json))
rule            (id, flow_id, node_id, label, question_text, answer_type,
                 evaluation(json), kind[KNOCKOUT|SCORED], weight, rule_class,
                 job_relatedness, created_at)
rule_class      (id, name, knockout_allowed(boolean), starter_question, starter_copy)
candidate       (id, name, email, job_ref, created_at)
screening_session (id, candidate_id, flow_id, flow_version, status, started_at, ended_at)
screening_answer  (id, session_id, node_id, rule_id, raw_answer, result[PASS|FAIL], reason, ts)
screening_result  (id, session_id, status[PASSED|NEEDS_REVIEW], passed_count, total_count,
                   knockout_failed(boolean), created_at)
audit_entry     (id, session_id, actor, entry, trace_id, ts)   -- APPEND ONLY
```

Phase 2 (scheduling): `interviewer`, `interviewer_availability`, `interview_booking`.

---

## 5. Flow node types

`type` ∈ { `message`, `question`, `faq`, `screening_gate`, `schedule_handoff`, `human_handoff`, `end` }.

- **message** — bot says `prompt_text`, then `next_node_id`.
- **question** — `answer_type` ∈ { choice, number, text }. choice shows `quick_replies` buttons.
  May attach a `rule` (screening). Branches via `branches` (answer → next_node_id).
- **faq** — deterministic KB lookup; answers and returns to flow (never a dead end).
- **screening_gate** — evaluates accumulated rule results; routes pass vs needs-review.
- **schedule_handoff** — phase 2 hook (placeholder node for now).
- **human_handoff** — sets NEEDS_REVIEW, ends with a "a recruiter will follow up" message.
- **end** — terminal; closing remarks.

Every node carries its own editable copy: `prompt_text`, `quick_replies`, `fallback_text`,
`transition_text`. (Research: tone must be consistent across greeting, answers, fallback, errors.)

---

## 6. Rule schema (core)

Rules are the regulated surface. JSON shape (also the export/import contract):

```json
{
  "id": "auth",
  "label": "Work authorization",
  "questionText": "Are you authorized to work in the US without sponsorship?",
  "answerType": "choice",
  "options": ["Yes", "No"],
  "kind": "KNOCKOUT",
  "ruleClass": "WorkAuth",
  "evaluation": { "operator": "equals", "value": "Yes" },
  "jobRelatedness": "Legal authorization is a non-negotiable requirement to be employed for this req.",
  "weight": null
}
```

- `kind`: `KNOCKOUT` (binary, auto-disqualify → NEEDS_REVIEW) or `SCORED` (weighted, recruiter review).
- `evaluation.operator`: equals | notEquals | gte | lte | contains. `value`: typed per answerType.
- `jobRelatedness`: **required** to save a KNOCKOUT (the BFOQ paper trail).
- `ruleClass`: from the safe library (§8).

---

## 7. Conversation text layer (copy requirements, research-backed)

- **Welcome** node: short, friendly, states purpose + value, offers common tasks
  ("find a role, check requirements, book an interview").
- **Short messages**: chunk long content; one clear next step per turn.
- **Quick replies** preferred over free typing (mobile-first; scopes input).
- **Fallback**: never a dead-end apology — show closest-matching options + a path forward, and a
  human-handoff option.
- **One tone** applied to greeting, answers, fallback, and error copy.
- **Typing indicator** between turns; **decision-trail** panel visible during screening.

---

## 8. Compliance guardrails (engine-enforced)

1. **Human-in-the-loop.** Failed/knockout → `NEEDS_REVIEW`, never auto-reject. Recruiter decides.
2. **Knockout class restriction.** `rule_class.knockout_allowed` gates it. Seed the safe library:
   - allowed = true: `WorkAuth`, `License/Cert`, `BackgroundCheckConsent`, `Location/Onsite`,
     `Availability` (hard dealbreaker only).
   - allowed = false: `ExperienceYears`, `SalaryExpectation`, `SoftSkill`. If an author marks one of
     these KNOCKOUT, the API rejects it with a clear message (offer SCORED instead).
3. **BFOQ justification required** for any KNOCKOUT.
4. **Append-only audit**; never update/delete `audit_entry`. Log prompt, answer, each rule eval,
   final decision, with `trace_id`.
5. **Retention**: keep audit ≥ 4 years (configurable). (CA law precedent for automated-decision data.)
6. **Immutable published versions**; runtime executes a PUBLISHED flow/rule version.
7. **Preview/simulate** in admin: run the draft as a test candidate before publishing.
8. **Disparate-impact lint** (nice-to-have): warn on risky phrasings (e.g., "every Saturday").

---

## 9. Admin console features

- Flow list + builder: create/edit/reorder nodes, set branching, edit all copy fields.
- Rule builder: attach rules to question nodes; set kind/evaluation/class/justification.
- Rule library: safe `rule_class` entries with starter question + copy to clone.
- Preview/simulate: full runtime against the draft, no DB writes to real pipeline.
- Publish + version: snapshot draft → immutable PUBLISHED version.
- Audit viewer + candidate pipeline (counts by status, list, drill into a session's trail).
- Export/import the rule set as JSON (§6 shape).

---

## 10. Chat runtime features

- Load the PUBLISHED flow; execute the FSM deterministically.
- Render quick-reply buttons; number/text inputs; deterministic FAQ KB.
- Live decision trail (PASS/FAIL + reason + rule id) beside the chat.
- On completion: `PASSED` or `NEEDS_REVIEW` (knockout → NEEDS_REVIEW, not reject).
- Persist session + answers + result + audit via API.

---

## 11. API (sketch)

```
# runtime
GET  /api/flows/published?jobRef=...
POST /api/screening/sessions            -> { sessionId, firstNode }
POST /api/screening/sessions/{id}/answer { nodeId, answer } -> { result, nextNode }
GET  /api/faq?q=...

# admin
GET/POST/PUT/DELETE /api/admin/flows[/{id}]
GET/POST/PUT/DELETE /api/admin/flows/{id}/nodes[/{nodeId}]
GET/POST/PUT/DELETE /api/admin/rules[/{id}]
POST /api/admin/flows/{id}/publish
GET  /api/admin/rule-classes
GET  /api/admin/flows/{id}/export   |  POST /api/admin/flows/import

# review
GET  /api/candidates ; GET /api/sessions/{id}/audit ; GET /api/pipeline
```

---

## 12. Build phases

- **P1 — Backend core**: domain model, rule engine (pure-function evaluation), audit service,
  publish/versioning, seed data. Unit-test the engine and the knockout-class guardrail.
- **P2 — Chat runtime**: React runtime wired to the runtime API; decision trail; persistence.
- **P3 — Admin**: flow builder + rule builder + rule library + preview/simulate + publish.
- **P4 — Review**: audit viewer, candidate pipeline, JSON export/import.
- **P5 — Later**: scheduling (interviewer availability + constraint solver), SSO, Oracle, ATS adapter.

---

## 13. Project structure

```
/frontend            # Vite + React
  /src
    /api             # api client
    /runtime         # chat runtime views + decision trail
    /admin           # flow builder, rule builder, library, preview, audit, pipeline
    /store           # zustand
/backend             # Spring Boot
  /src/main/java/.../screening   # ScreeningService + rule engine
  /src/main/java/.../flow        # FlowService, publish/versioning
  /src/main/java/.../audit       # AuditService (append-only)
  /src/main/java/.../candidate   # CandidateService, pipeline
  /src/main/java/.../web         # REST controllers, DTOs
  /src/main/resources/db         # schema + seed
/careers             # bofa-careers-home.html (front door)
```

---

## 14. Seed data (starter flow + rules)

Reuse these roles as starter flows; each begins with a Welcome message, then screening questions.

- **Senior Java Developer** — WorkAuth (knockout), Java/Spring years (SCORED, ≥5), Spring+Oracle
  depth (SCORED), notice period (SCORED).
- **Oracle Developer** — WorkAuth (knockout), PL/SQL years (SCORED, ≥4), Onsite (knockout).
- **AI Engineer** — WorkAuth (knockout), production LLM shipped (knockout: License/Cert-style? No —
  treat as SCORED unless legally essential), eval/guardrails (SCORED).
- **Agent Engineer / Prompt Engineer / Product Designer** — WorkAuth (knockout) + 1–2 SCORED each.

FAQ KB starters: pay/comp, benefits, work mode, process timeline, sponsorship.

> Note the guardrail in action: "5+ years" must be SCORED, not KNOCKOUT (rigid experience thresholds
> screen out large shares of qualified candidates and carry legal risk).

---

## 15. First prompt to give Claude Code

> "Read CLAUDE.md and BUILD_SPEC.md. Scaffold the project structure in §13, then build Phase 1
> (backend core): domain model, the deterministic rule engine with the knockout-class guardrail,
> append-only audit, publish/versioning, and seed data. Write unit tests for the rule engine and the
> guardrail. Don't start the frontend yet."
