---
trigger: always_on
description: Universal BFD Execution Protocol — Feature-scoped specs (requirements/design/tasks/uat), model-tier modes, atomic task loop, scope guardrails, and cross-model review arbiter gate
---

# ⚙️ Rule: BFD Execution Protocol

**Purpose:** Deterministic, conflict-free execution across **Solo or Multi-Developer teams (2+ devs / parallel branches)** and across both **Frontier Models** (Gemini Pro/Argon, Claude Opus/Sonnet, Codex/o3, DeepSeek R1) and **Fast/Low-Tier Models** (Flash/Lite, Haiku, Grok Mini, local models).

---

## 1. Three-Folder `docs/` Brain + Two-Folder `.agents/` Engine

To prevent Git merge conflicts when 2+ developers or parallel AI agents work on the same project simultaneously, BFD separates **System Truths**, **Macro Roadmap**, **Micro Feature Specs**, and the **`.agents/` Engine**:

1. **System Foundation (`docs/foundation/`):** Project-wide permanent truths (`prd.md`, `architecture.md`, `tech-stack.md`, `design-system.md`, `decisions.md`).
2. **Macro Planning (`docs/planning/`):** Tracks project-wide phases in `roadmap.md` (including the **Feature Specs Registry & Dependency Matrix**), `backlog.md`, `history.md`, and `issues.md`.
3. **Micro Feature Specs (`docs/specs/<NN-feature-slug>/`):** Every feature group or ticket has its own isolated directory following the **4-Skill Lifecycle (`/spec` → `/build` → `/verify` → `/ship`)**:
   - `requirements.md` — **1. Specify (`/spec`):** Assumptions (`A1`), Glossary, User Stories, and numbered EARS Acceptance Criteria (`Req 1.1`, `Req 1.2`)
   - `design.md` — **2. Design (`/spec`):** Mermaid architecture flow, exact `[NEW]` / `[MODIFY]` files, Data Models, Error Handling Matrix, and Correctness Properties
   - `tasks.md` — **3. Build (`/build`):** Numbered tasks (`1.1`, `1.2`) with layer tags (`[BE]`, `[FE]`), `_Requirements: X.Y_` traceability, `- [ ]*` optional test tasks, `⚡ BATCH` tags, intra-feature `waves` JSON graph, and Activity Log
   - `uat.md` — **4. Verify & Ship (`/verify` → `/ship`):** 3-Level verification & Hard-Failure review checklist
4. **AI Engine (`.agents/`):**
   - `.agents/rules/` — Always-on guardrails (`execution-protocol.md` + `stack-rules.md`)
   - `.agents/skills/` — 5 self-contained lifecycle skills:
     - [`next/SKILL.md`](../skills/next/SKILL.md) (`/next`) — AI Product Researcher & Next-Wave Advisor
     - [`spec/SKILL.md`](../skills/spec/SKILL.md) (`/spec`) + `spec/resources/*.template`
     - [`build/SKILL.md`](../skills/build/SKILL.md) (`/build`) + `build/examples/*.template`
     - [`verify/SKILL.md`](../skills/verify/SKILL.md) (`/verify`)
     - [`ship/SKILL.md`](../skills/ship/SKILL.md) (`/ship`)

> **Multi-Dev & Parallel Agent Rules:**
> 1. **How to Know Which Feature to Build First (or in Parallel):** Check the **Feature Specs Registry & Dependency Matrix** in `docs/planning/roadmap.md`. Every feature has a **`Wave`**, a **`Depends On`** column, and a **`Touches (Isolation Zone)`** column:
>    - A feature is **Ready to Build** only when **all** specs listed in its `Depends On` column are marked `[x]`.
>    - Two developers or parallel AI agents can work **simultaneously** on any two features that are both Ready to Build, have **non-overlapping `Touches` paths**, and have each claimed their row (`Status: [>]`, `Branch / Owner: feat/<slug> (@dev-or-agent)`).
> 2. **Intra-Feature Parallelism:** Inside a single feature's `tasks.md`, subagents can also execute sub-tasks in parallel according to the `## 🔀 Task Dependency Graph (Intra-Feature Waves)` JSON block at the bottom of `tasks.md`.
> 3. **File Isolation During Coding:** During daily coding on a feature branch (`feat/<feature-slug>`), **all** task marker updates (`[>]` → `[x]`) and Activity Log entries happen inside `docs/specs/<NN-feature-slug>/tasks.md`. Only touch `docs/planning/roadmap.md` when claiming (`[>]`) or shipping (`[x]`) a feature spec!

---

## 2. Model-Tier Execution Modes

Operate in the mode that matches your model capability (or as instructed by the user):

### A. Strict Atomic Mode *(Default — Mandatory for Low/Fast Models)*
- **Scope:** Execute **1 sub-task (`1.1`, `1.2`)** at a time from `docs/specs/<NN-feature-slug>/tasks.md`.
- **Context Cap:** Never load more than **4 files** at once (`AGENTS.md`, `docs/specs/<NN-feature-slug>/design.md`, `docs/specs/<NN-feature-slug>/tasks.md`, and the target code/example file).
- **Design & Example First:** Follow `design.md` and copy from `.agents/skills/build/examples/` if a matching template exists before writing custom logic.
- **Verify Every Step:** Run the stack's verification command after **every** task before marking `[x]`.

### B. Coupled Batch Mode *(Frontier Models — or `⚡ BATCH` tasks)*
- **Scope:** When tasks in `docs/specs/<NN-feature-slug>/tasks.md` are marked `⚡ BATCH` (compile-time or runtime coupled, such as DB migration + Route Handler + UI component), declare:
  > *"Executing coupled batch in `docs/specs/<NN-feature-slug>/tasks.md`: [list task IDs]."*
- **Execution:** Implement the coupled tasks together, run full verification (`build` + `typecheck` + `lint`) across the batch, then mark all completed batch tasks `[x]`.

---

## 3. The Standard Task Loop (`/build`)

For every task (or declared `⚡ BATCH` group) in the active `docs/specs/<NN-feature-slug>/tasks.md`:

1. **ORIENT** — Read `docs/specs/<NN-feature-slug>/tasks.md` and `design.md` to identify the active `[>]` task (or pick the next `[ ]` task and mark it `[>]`).
2. **CHECK BOUNDARIES** — Verify the target file path against `docs/foundation/architecture.md` and `.agents/rules/stack-rules.md` (Layer Boundaries) and confirm the work is **not** on the `AGENTS.md` Anti-Hallucination List.
3. **IMPLEMENT** — Write the smallest scoped code that satisfies the task and its traced `_Requirements: X.Y_` using `docs/specs/<NN-feature-slug>/design.md` and `.agents/skills/build/examples/`.
4. **VERIFY (Mandatory)** — Run deterministic verification in the terminal:
   - **Next.js / TypeScript:** `pnpm tsc --noEmit` (and `pnpm build` at Checkpoints / feature completion)
   - **WordPress / PHP:** `php -l <file.php>` on all touched PHP files
   - If verification fails, **do not mark `[x]`**. Fix or follow the 3-Strike Rule below.
5. **SYNC FEATURE TRACKER** — Immediately update `docs/specs/<NN-feature-slug>/tasks.md` (`[>]` → `[x]`) and append to its Activity Log. When all tasks in `tasks.md` are `[x]`, run **`/verify`** (`.agents/skills/verify/SKILL.md`) and **`/ship`** (`.agents/skills/ship/SKILL.md`).
6. **AUTOMATIC GIT BRANCH & PR ROUTER (Zero Human Guessing):**
   - **Auto-Branch Before Coding (`01+` Features & Multi-Feature Waves):** Before editing code on any `01+` feature (or Mode B wave), if the current branch is `main`, `master`, or `develop`, **automatically create and switch to a feature branch** (`git checkout -b feat/<NN-feature-slug>` or `feat/wave-<N>`). *(Only `00-scaffold` or Master Brain maintenance may commit directly on `main` in solo repos.)*
   - **Context-Aware Ship & PR Routing (`/ship`):**
     - **Company / Team / Client Repo** (`Context: Tangible` / `Team` / `Client` in `AGENTS.md`, `.git/info/exclude` contains `.agents/`, or 2+ Git contributors): **Never push or merge directly to `main`.** Respect `.git/info/exclude` stealth exclusions, push `origin feat/<NN-feature-slug>`, and create/output a PR.
     - **Open-Source / CI-Protected Repo** (`Context: Open-Source`, `.github/workflows/` exists, or `CONTRIBUTING.md` exists): Push `origin feat/<NN-feature-slug>` and open a PR so CI runs and `main` stays protected.
     - **Solo Private MVP (`Context: Personal`):** Commit atomically on `feat/<NN-feature-slug>` and ask once at `/ship` (or follow user preference): *"Merge `feat/<NN-feature-slug>` cleanly into `main` now, or push branch for a PR?"*
   - Parallel subagents must **never** run `git add .` or `git commit` mid-wave; only the Main Agent commits each feature after root verification passes.

---

## 4. Budget Guard & Loop Prevention

### Pause & Ask Thresholds
Pause and ask for confirmation (`"⚠️ BUDGET GUARD TRIGGERED: [reason]. Awaiting approval..."`) before:
1. **Unplanned Multi-File Edits:** Touching **more than 3 files** in a single turn *unless* executing a declared `⚡ BATCH` group defined in `docs/specs/<NN-feature-slug>/tasks.md`.
2. **Destructive Actions:** Deleting files, dropping database tables/columns, or overwriting environment/config files.
3. **High-Blast-Radius Changes:** Modifying shared types (`shared/types/`), auth middleware, or database schemas without running `/spec` (Mode C: Blast-Radius Check) first.

### The 3-Strike Rule (Loop Prevention)
If the same error persists after **3 fix attempts**:
1. **STOP** immediately — do not attempt a 4th blind retry.
2. **REPORT** the exact error, the 3 approaches tried, and why each failed.
3. **ASK** the user for guidance.

---

## 5. Task Marker Syntax (`docs/specs/<NN-feature-slug>/tasks.md`)

| Marker | Meaning | Rule |
| :--- | :--- | :--- |
| `- [ ]` | Pending | Ready to pick up in sequence |
| `- [ ]*` | Optional Test Task | Unit / property-based test sub-task (recommended for compliance; can be skipped (`[-]`) for fast MVP) |
| `- [>]` | Active | **Only ONE `[>]` task** (or `⚡ BATCH` group) per `docs/specs/<NN-feature-slug>/tasks.md` |
| `- [x]` | Complete | Implementation exists AND verification command passed |
| `- [!]` | Blocked | External blocker or failed verify — always append reason on the same line |
| `- [-]` | Deferred | Explicitly skipped — append reason |
| `⚡ BATCH` | Coupled | Indicates task group is compile/runtime coupled |

---

## 6. Right-Sized Routing: New Feature vs. Bug Fix vs. Small Update

Match the skill to the size of the work:

| Scenario | Criteria | Skill to Use | Where It's Tracked |
| :--- | :--- | :--- | :--- |
| **A1. Single Roadmap Feature** | Next unblocked feature in `docs/planning/roadmap.md` | **`/spec`** (if `design.md` needed) → **`/build`** (Mode A) → **`/verify`** → **`/ship`** | `docs/specs/<NN-feature-slug>/` |
| **A2. Multi-Feature / Full Phase (`2–15+ features`)** | Automating an entire Wave or Phase (`Phase 1` / `all`) | **`/goal /build Phase 1`** (Mode B Wave Orchestrator: spawns 1 fresh `self` subagent per feature wave-by-wave to prevent context bloat!) | `docs/planning/roadmap.md` + `docs/specs/<NN-feature-slug>/` |
| **B. New Unplanned Feature / Ticket** | New scope not yet in `roadmap.md` | **`/spec`** (creates spec folder + adds row to `roadmap.md`) → **`/build`** | `docs/specs/<NN-feature-slug>/` + `docs/planning/roadmap.md` |
| **C. Small Update / Polish (`<= 2 files`, `< 30 min`)** | Copy tweak, UI styling adjustment, or minor validation | **Fast-Track:** Skip creating a new spec folder! Append a `- [ ]` task under `## 🐛 Ad-Hoc / Small Enhancements` in the **existing** `docs/specs/<NN-feature-slug>/tasks.md` → **`/build`** | Existing `docs/specs/<NN-feature-slug>/tasks.md` |
| **D. Bug Fix** | Broken build, runtime error, or regression | Fix + add regression unit test + run terminal verification → log under `## 📝 Feature Activity Log` in the related `docs/specs/<NN-feature-slug>/tasks.md` + run **6.1 Automatic Self-Healing Check** below (or run **`/spec`** for `fix-<bug-slug>` if 4+ files) | Related `docs/specs/<NN-feature-slug>/tasks.md` + auto-routed Brain updates |
| **E. External Security / Code Audit (`docs/audits/*.md`)** | Third-party or teammate audit `.md` report | **`/verify docs/audits/<report>.md`** → Auto-triages live code (`✅ ALREADY FIXED`, `🛡️ REJECT`, `🔴 OPEN P0/P1`) and auto-routes: **Direct Fix** (`<= 3 files`) vs. **Remediation `/spec`** (`4+ files` or DB/Auth changes) | `docs/audits/<report>.md` + Direct Fix or `docs/specs/<NN-security-remediation>/` |

### 6.1 Automatic Self-Healing After Any Bug Fix or User Correction (Zero Extra Commands)

Humans should **never** have to remember an extra slash command or figure out which file a lesson belongs in after a debugging session. **Every time** you fix a user-reported bug, resolve a runtime error, or get corrected by the user, you **MUST automatically execute this regression test + 3-bucket check before ending your turn**:

0. **Step 0 — Executable Regression Test ("Past Incidents as Evals"):**
   - *Rule:* Markdown rules alone can be forgotten; automated tests cannot. Whenever you fix a runtime/logic bug or a `P0`/`P1` review defect, add or update a test case in the feature's unit/eval test suite (`__tests__/`, `tests/Unit/`, or `tests/evals/`) that reproduces the exact bug and asserts the fix before running terminal verification.
1. **Bucket 1 — Project Guardrail (`AGENTS.md` / `docs/foundation/decisions.md` / `design.md`):**
   - *Question:* Did this bug happen because of a project-specific convention, missing edge case in `design.md`, or hallucinated helper/import?
   - *Action:* Immediately add a 1-line rule to `AGENTS.md` (Anti-Hallucination / Project Rules), `docs/foundation/decisions.md`, or the active `design.md` Error Matrix so no future wave or subagent repeats it in this repo.
2. **Bucket 2 — Project Brain Issue Log (`docs/planning/issues.md`):**
   - *Question:* Did the agent misbehave, loop on an error, or violate a workflow rule?
   - *Action:* Automatically append an entry to `docs/planning/issues.md` (creating it from `issues.md.template` if missing).
3. **Bucket 3 — Master Brain Signal (`shikamaru/feedback.md`):**
   - *Question:* Did this bug reveal a stack-wide gotcha, framework nuance, or missing template that **every future project** on this stack should inherit?
   - *Action:* Output a ready-to-copy 1-line signal at the bottom of your reply (`🧠 Master Brain Signal: YYYY-MM-DD | [project] | [skill] | [RULE/TEMPLATE/UAT] | [signal]`) — and if `shikamaru/feedback.md` is accessible in the workspace, append it directly.

---

## 7. Cross-Model Review & Security Audit Protocol (Anti-Ping-Pong Gate)

To prevent multi-model review ping-pong (where Gemini, Claude, and Codex contradict each other with competing stylistic opinions or undo settled tradeoffs):

### A. When Asked to Review Code or Run a Security Audit (Even Without `/verify`)
1. **Read the Contract First:** Before flagging any issue, check `docs/foundation/decisions.md`, `.agents/rules/stack-rules.md`, and the relevant `docs/specs/<NN-feature-slug>/{requirements,design}.md`. Never flag an intentional tradeoff documented in `decisions.md` or `design.md` (or listed in `requirements.md` Non-Goals).
2. **Enforce Strict Severity Tiers (Evidence Only):**
   - **`[P0 — Blocker]`:** Provable runtime bug, data loss/corruption, exploit/security vulnerability (missing auth/RLS/nonce/capability, SQLi, XSS, secret leak), broken EARS requirement, or `.agents/rules/stack-rules.md` Hard-Failure violation. **Must include exact `file:line` and a concrete trigger/exploit path.**
   - **`[P1 — Defect]`:** Unhandled error path defined in `design.md` Error Matrix, race condition, or bounded N+1/memory leak under realistic load. **Must include `file:line` and concrete failure scenario.**
   - **`[P2 — Nitpick / Subjective Style]`:** Naming preferences, DRY vs. inline philosophy, hypothetical scale concerns, or redundant defensive checks already guaranteed by types/framework. **DO NOT report `P2` items unless explicitly asked for stylistic suggestions.**
3. **Zero Forced Fault-Finding:** If zero `P0` or `P1` defects exist, report **`✅ 0 P0/P1 Findings — Ready to Ship`**. Never invent nitpicks to appear thorough.

### B. Arbiter Mode: Triaging External Reviews & Audit Files (`docs/audits/*.md`)
When a teammate or external tool drops a security/code audit report in `docs/audits/*.md` (or pastes findings from Claude, Codex, or another reviewer):
1. **Never Blindly Apply External Feedback:** Act as the **Arbiter** and inspect the live codebase before changing any code or generating tasks.
2. **Classify Each External Finding into 3 Buckets Against the Live Code & Brain:**
   - **`✅ ALREADY FIXED`:** The live codebase already contains the guard or fix (cite exact `file:line`). Skip it so stale audit items never become duplicate work.
   - **`🛡️ REJECT` (`P2` / False Positive / Settled Decision):** Subjective refactor preference, contradicts `docs/foundation/decisions.md` or `design.md`, or is already protected by an upstream boundary (e.g., middleware, RLS, Zod/Pydantic schema, WP nonce/capability check). Draft a 1-line rebuttal citing the exact `file:line` or ADR so the developer can reply to the reviewer.
   - **`🔴 OPEN P0 / P1` (Actionable Defect / Vulnerability):** Verified still-open bug or exploit path in the current code (with exact `file:line`).
3. **Automatic Right-Sized Remediation Router (Direct Fix vs. New `/spec`):**
   - **Direct Fix (`<= 3 files` touched AND zero DB/Auth architecture changes):** Fix immediately during `/verify`, add regression unit tests (§6.1), and run terminal verification.
   - **Remediation `/spec` (`4+ files` touched OR changes DB schema / Auth flow / Shared contracts):** Do not make blind multi-file edits inline; route ONLY the verified `🔴 OPEN P0 / P1` items into a dedicated `docs/specs/<NN-security-remediation>/` bundle via `/spec`.
4. **1-Pass Review Cap:** Perform at most **1 Arbiter triage + fix pass** per feature/PR. Once deterministic checks (`build`/`typecheck`/`tests`) and `P0`/`P1` items pass, ship.
