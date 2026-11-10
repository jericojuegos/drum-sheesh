# 🗺️ Project Context Map — drum-sheesh

> **For AI Agents (All Models) & Multi-Dev Teams:** Use this index to load **only** the files needed for your current task.
> Do not read the entire `docs/` or `.agents/` folder at once.

---

## 1. Core Entrypoints (Read First Every Session)

| File | Purpose | When to Read |
| :--- | :--- | :--- |
| [`AGENTS.md`](../AGENTS.md) | Project state, Critical Constraints, Anti-Hallucination List, Verify commands | **Every session start** |
| [`docs/planning/roadmap.md`](./planning/roadmap.md) | Macro phases + **Feature Specs Registry & Dependency Matrix** (`docs/specs/<slug>/`) | **Every session start** |
| [`docs/specs/<NN-feature-slug>/tasks.md`](./specs/) | Active feature's numbered task checklist (`1.1`, `1.2`, `⚡ BATCH`), Wave graph, & Activity Log | **Every session start & task completion** |
| [`.agents/rules/execution-protocol.md`](../.agents/rules/execution-protocol.md) | Atomic vs. Batch loop, multi-dev wave isolation, 3-Strike rule, right-sized routing | **Always active** |
| [`.agents/rules/stack-rules.md`](../.agents/rules/stack-rules.md) | Layer boundaries, folder responsibilities, security & coding standards | **Always active when coding** |

---

## 2. Core Skills (`.agents/skills/`) — 5 Slash Commands

| Skill / Command | Location | What It Does |
| :--- | :--- | :--- |
| **`/next`** | [`.agents/skills/next/SKILL.md`](../.agents/skills/next/SKILL.md) | **AI Product Researcher & Next-Wave Advisor:** Tells you where the project left off, researches competitor features/gaps, and proposes the top 3 next feature waves to build |
| **`/spec`** | [`.agents/skills/spec/SKILL.md`](../.agents/skills/spec/SKILL.md) | Create or expand `docs/specs/<NN-feature-slug>/{requirements,design,tasks,uat}.md` using [`spec/resources/`](../.agents/skills/spec/resources/) templates |
| **`/build`** | [`.agents/skills/build/SKILL.md`](../.agents/skills/build/SKILL.md) | Execute Phase 0 `00-scaffold` or feature waves in `tasks.md` using [`build/examples/`](../.agents/skills/build/examples/) code patterns |
| **`/verify`** | [`.agents/skills/verify/SKILL.md`](../.agents/skills/verify/SKILL.md) | Run Level 1 (Build/Types), Level 2 (EARS Smoke Tests), and Level 3 (Stack Hard-Failure & UX Review) against `uat.md` |
| **`/ship`** | [`.agents/skills/ship/SKILL.md`](../.agents/skills/ship/SKILL.md) | Mark feature `[x]` in `roadmap.md`, sync `docs/foundation/` if needed, archive completed phases, and generate PR summary |

---

## 3. Micro Feature Spec Bundles (`docs/specs/<NN-feature-slug>/`) — Per Feature / Per Branch

Each feature group or ticket has its own isolated directory under `docs/specs/<NN-feature-slug>/` so multiple developers or parallel AI agents never conflict:

| Lifecycle Step | File in `docs/specs/<NN-feature-slug>/` | Purpose |
| :--- | :--- | :--- |
| **1. Specify (`/spec`)** | `requirements.md` | Assumptions (`A1`), Glossary, User Stories, and numbered EARS Acceptance Criteria (`Req 1.1`, `Req 1.2`) |
| **2. Design (`/spec`)** | `design.md` | Mermaid architecture flow, exact `[NEW]`/`[MODIFY]` files, Data Models, Error Handling Matrix, Correctness Properties |
| **3. Build (`/build`)** | `tasks.md` | Numbered tasks (`1.1`, `1.2`) with layer tags (`[BE]`, `[FE]`), `_Requirements: X.Y_` traceability, `⚡ BATCH`, & Wave JSON graph |
| **4. Verify (`/verify`)** | `uat.md` | Feature UAT checklist (Level 1 automated, Level 2 EARS functional, Level 3 human UX sign-off) |
| *(Visuals)* | `design-reference/` | Optional Stitch exports or Figma screenshots scoped to this feature only |

---

## 4. Project Foundation (`docs/foundation/`) — System-Wide Truths (Load On Demand)

| File | Contains | Read When... |
| :--- | :--- | :--- |
| [`docs/foundation/prd.md`](./foundation/prd.md) | Product vision, user personas, pricing/credits, high-level user journey | Scoping new feature specs or verifying product-wide business rules |
| [`docs/foundation/architecture.md`](./foundation/architecture.md) | System topology, folder map, global DB schema, data flows | Placing new files, writing API/DB logic, checking layer imports |
| [`docs/foundation/tech-stack.md`](./foundation/tech-stack.md) | Packages, key constants, env vars, external deps, prohibited patterns | Installing packages, adding constants, configuring integrations |
| [`docs/foundation/design-system.md`](./foundation/design-system.md) | Color tokens, typography, spacing, visual tone, UI mode | Building or styling any UI component or page |
| [`docs/foundation/decisions.md`](./foundation/decisions.md) | Architecture Decision Records (ADRs) | Checking why a pattern was chosen or logging a new architectural choice |

---

## 5. Macro Project Planning (`docs/planning/`) — Load When Scoping Phases

| File | Contains | Read When... |
| :--- | :--- | :--- |
| [`docs/planning/roadmap.md`](./planning/roadmap.md) | Phased milestones + Feature Dependency & Parallel Wave Matrix | Checking phase sequence or picking the next unblocked feature spec |
| [`docs/planning/backlog.md`](./planning/backlog.md) | Deferred features (`[-]`) and permanently rejected features | Auditing scope or deferring an item |
| [`docs/planning/history.md`](./planning/history.md) | Archived completed phases and phase retrospectives | Running `/ship` at phase completion |
| [`docs/planning/issues.md`](./planning/issues.md) | Brain/workflow issue log | Logging an agent/workflow gap |
| [`docs/audits/`](./audits/) | External security & code audit reports (`docs/audits/*.md`) | Running `/verify docs/audits/<report>.md` to auto-triage (`ALREADY FIXED` / `REJECT` / `OPEN P0/P1`) & route fixes |
