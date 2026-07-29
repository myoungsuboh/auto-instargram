# 00-ORCHESTRATOR: Vibe Coding Master Plan

> **Agent Instructions:**
> You are ONE coding agent executing this plan sequentially. **DO NOT SKIP PHASES.**
> **Recommended runtime:** this package is designed to complete reliably on Claude Sonnet / GPT-4-class (or stronger) agents; lighter models will need noticeably more STOP interventions.
> For each task, if a `Target Skill` is assigned (not `none`), read that file from the `skills/` directory before writing code.
> Write durable intermediate artifacts to `_workspace/` and re-read them (and the relevant spec file) at the start of each phase — do not trust memory.
> After each phase: run its Verify step, loop fixes until green (max 3), then STOP and wait for the user's confirmation.
> **Never push during the build:** no `git push` or any remote upload until the Final Phase's SHIP_TO_HARNESS step — commits stay local until then.
> **Database writes (only if the plan has a database):** before EVERY migration/seed run the target must be local (`localhost`/`127.0.0.1`/a service from this project's own docker-compose) — anything else: STOP and ask the user first. Seed idempotently per entity (skip a table/collection that already has rows — NOT the whole database, or later phases would never seed).
> **Screens (only if the design has frontend screens):** every screen ships wired to the real BE API — no mock or hardcoded data left on the primary path. The Integration phase's Verify must drive one primary user flow through the real FE against the running BE (FE → BE → DB when one exists) and record the commands + observed results in its STOP report.
> **Runnable handoff (Principle 10):** the final package MUST include a committed run entrypoint (stack-appropriate `run.sh`/`run.bat`, or documented ordered commands) + a README "How to run" section, and the Final Phase MUST verify it starts from a clean state — so a non-developer can run it.
> **Resuming after an interruption?** Read `IMPLEMENTATION-CHECKLIST.md` and `_workspace/` first to see what is already done, then continue from the first unchecked item — do not start over.

## Project Size: Standard
**Rationale:** The project contains 5 APIs, 3 entities, 5 policies, 4 screens across 2 bounded contexts (Instagram 게시 관리, 인스타그램 보안 관리) utilizing Spring Boot, Vue.js, and PostgreSQL.

## Phase 1: Foundation & Project Structure
- [ ] Task 1.1: Initialize project structure. Create root directory split into `FE/` (Vue.js) and `BE/` (Spring Boot), then initialize `git init -b main` with a robust `.gitignore` excluding `node_modules/`, `build/`, `.gradle/`, `target/`, and `.env`. (Target Skill: skills/core/git-git-workflow.md)
- [ ] Task 1.2: Define domain models (HistoryRecord, QueueItem, SecurityCredential) from the DDD and Spack specs, mapping them to relational schema standards, and save schemas to `_workspace/db_schema.json`. (Target Skill: skills/db/snake_case-db-common-conventions.md)
- [ ] Task 1.3: Detect an existing database in the codebase first. If none: stand up PostgreSQL via `docker-compose.dev.yml` + `.env.example` using strict environment wiring. Run initial schema migrations for `HistoryRecord`, `QueueItem`, and `SecurityCredential` with soft-delete and audit columns. Never write to a remote database without asking. (Target Skill: skills/db/soft-delete-soft-delete-audit.md)
- [ ] Verify 1: Build both `FE/` and `BE/` (verify Spring Boot compiles and PostgreSQL connection check passes via migrations) → fix-loop until green (max 3) → adversarial self-review → commit (`git add -A && git commit`) → **STOP for user confirmation**

## Phase 2: Domain: Instagram Security & Tokens Context
- [ ] Task 2.1: Read `_workspace/db_schema.json`; implement the Security domain entities (`SecurityCredential`), token refresh logic (`POST /api/v1/tokens/refresh`), and JWT authentication/authorization filters in `BE/` honoring policies `POL-01` to `POL-05`. Record contracts in `_workspace/api_contracts.md`. (Target Skill: skills/security/JWT-authn-authz.md)
- [ ] Task 2.2: Implement secure handling of secrets, environment configuration, input sanitization, and OWASP top 10 safeguards for the security credential context. (Target Skill: skills/security/env-secrets-management.md)
- [ ] Verify 2: Run backend unit tests and integration tests for security and token refresh endpoints; seed initial admin credentials idempotently (state test credentials in the STOP report); fetch seeded security state through a real API call → fix-loop (max 3) → adversarial self-review → commit (`git add -A && git commit`) → **STOP for user confirmation**

## Phase 3: Domain: Instagram Post & Queue Management Context
- [ ] Task 3.1: Read `_workspace/db_schema.json` and `_workspace/api_contracts.md`; implement `QueueItem` and `HistoryRecord` entities and their corresponding APIs: `POST /api/v1/queues`, `GET /api/v1/queues`, `GET /api/v1/history`, and `POST /api/v1/reels/upload` in `BE/`. Ensure idempotency for queue registration and reliable error handling/resilience. (Target Skill: skills/backEnd/idempotency-idempotency.md)
- [ ] Task 3.2: Integrate S3-compatible file storage for reels upload pipeline (`POST /api/v1/reels/upload`) with secure presigned upload handling. (Target Skill: skills/backEnd/s3-file-storage.md)
- [ ] Verify 3: Run Spring Boot test suite covering queue creation, history lookup, and reels upload; seed test queue items and history records idempotently; execute a real API round-trip using `curl` → fix-loop (max 3) → adversarial self-review → commit (`git add -A && git commit`) → **STOP for user confirmation**

## Phase 4: Frontend UI Dashboards Implementation
- [ ] Task 4.1: Read `_workspace/api_contracts.md`; initialize Vue.js frontend with proper coding standards and design style (Portfolio Showcase - Warm). Implement Axios/Fetch API client matching backend routes. (Target Skill: skills/design/design-style-style-portfolio-showcase.md)
- [ ] Task 4.2: Implement all 4 frontend screens (`/dashboard/upload`, `/dashboard/posts`, `/dashboard/history`, `/dashboard/reels`) wired directly to the real `BE/` APIs — no mock or hardcoded data left on the primary path. Implement input validation and XSS sanitization. (Target Skill: skills/frontEnd/eslint-coding-styles.md)
- [ ] Verify 4: Run frontend build and test tools (`npm run build`, linting); verify code compliance → fix-loop (max 3) → adversarial self-review → commit (`git add -A && git commit`) → **STOP for user confirmation**

## Phase 5: End-to-End Integration & Verification
- [ ] Task 5.1: Connect services per the Architecture connection map (`Mobile Web Frontend` ↔ `Instagram Automation Backend` ↔ `PostgreSQL`); finalize cross-origin resource sharing (CORS), transport security headers, and end-to-end integration flow. (Target Skill: skills/security/HTTPS-transport-security.md)
- [ ] Verify 5: Start BE and FE together; drive one primary user flow through the real FE against the real BE (FE → BE → DB round-trip) and record the exact commands + observed results in the STOP report → fix-loop (max 3) → **STOP for user confirmation**

## Final Phase: Full-Coverage Audit Loop & Runnable Handoff
- [ ] Task 6.1: Open `IMPLEMENTATION-CHECKLIST.md`. For EVERY item (APIs, Entities, Policies, Screens, Domain Events, Services): verify it exists in real code, mark `- [x]`, and write the actual file path after `←구현위치:`.
- [ ] Task 6.2: Any item without a real file path = NOT implemented → implement it → re-run this audit from the top. Repeat until 100% checked. Report completion ONLY with the final audit result (`N/N ✅`) and the checked list.
- [ ] Task 6.3: Create committed run entrypoints (`run.sh` and `run.bat`) at the project root that automatically bring up Docker infra (`docker compose up -d`) and start both BE and FE. Write a clear "How to run" section in the README for non-developers.
- [ ] Task 6.4: Verify from a CLEAN state (`docker compose down -v`, then running the entrypoint script) that the app starts successfully and health checks pass with no manual steps beyond the README instructions.
- [ ] Task 6.5: After 100% audit completion, open `SHIP_TO_HARNESS.md` and execute its procedure YOURSELF (git setup, GitHub upload). Guide the user back to Harness: load the repository on the **Code** screen and run **Lint verification** on the **Lint** screen to complete the delivery loop.