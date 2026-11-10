---
name: ship
description: >-
  Finalize a verified feature or phase: mark the feature [x] in docs/planning/roadmap.md,
  sync docs/foundation/ if architecture or schema changed, archive completed phases to
  docs/planning/history.md, and generate a Requirement-traceable commit or PR summary.
  Use when a feature passes /verify or when the user invokes /ship.
---

# 🚀 Skill: Ship Feature & Sync Brain (`/ship`)

This skill closes the loop on a verified feature (`docs/specs/<NN-feature-slug>/`) or a completed phase.

---

## Step 1 — Pre-Ship Gate Check

1. Check `docs/specs/<NN-feature-slug>/tasks.md` and `uat.md`:
   - Confirm all required implementation tasks are `[x]`.
   - Confirm `uat.md` has passed **`/verify`**. (If not yet verified, run `../verify/SKILL.md` first.)
2. Run `git status` and `git diff --stat` to confirm no `.env*` secrets, debug logs, or untracked junk files are staged.

---

## Step 2 — As-Built Spec Reconciliation & Macro Brain Sync

1. **As-Built Spec Clean-Up (If Feature Had Mid-Build or Post-Build Iterations):**
   - Verify that `docs/specs/<NN-feature-slug>/requirements.md` (Sections 1–4) and `design.md` reflect **100% of the final code as actually built** (no leftover removed features or outdated behavior in active sections).
   - Ensure any post-build additions, changes, or removals are logged cleanly in `requirements.md` (`## 5. 📝 Revision History`) and `tasks.md` (`## 📝 Feature Activity Log`).
2. **Update `docs/planning/roadmap.md`:**
   - In the **Feature Specs Registry & Dependency Matrix**, change the feature's `Status` from `[>]` to `[x]` (and update its 1-line summary if the feature scope pivoted during iteration).
   - Check off the corresponding feature items in the Phase checklist.
   - Identify which next-wave features in the Dependency Matrix are now **unblocked**!
3. **Check `docs/foundation/` Drift (`prd.md`, `architecture.md`, `tech-stack.md`, `decisions.md`):**
   - Did iterating on this feature add/remove a product capability in `prd.md`, change a database table/route in `architecture.md`, add a package in `tech-stack.md`, or establish a new architectural rule in `decisions.md`?
   - If YES → Update those `docs/foundation/` files **before committing** so the project brain is 100% truthful before moving to Feature 2!
4. **Automatic Session Retro & Brain-Gap Routing (Zero Human Guessing):**
   - Review the feature's session history and `tasks.md` Activity Log for any errors, retries, hallucinations, or user bug reports fixed along the way.
   - Automatically route each lesson into the right place so the user never has to ask where it goes:
     - **Bucket 1 (Project Rule):** Add project-specific guardrails to `AGENTS.md` or `docs/foundation/decisions.md`.
     - **Bucket 2 (Project Issue Log):** Ensure any agent workflow failure or loop is recorded in `docs/planning/issues.md`.
     - **Bucket 3 (Master Brain Signal):** If a stack-wide rule, framework gotcha, or missing template was discovered, include the ready-to-promote line (`YYYY-MM-DD | [project] | /ship | [RULE/TEMPLATE/UAT] | [signal]`) in the Ship Summary below (and append to `shikamaru/feedback.md` if accessible).
5. **If Entire Phase Is Complete:**
   - Summarize the completed phase into `docs/planning/history.md` (with completion date, features shipped, iterations/pivots made, and key lessons).
   - Update `AGENTS.md` `## 🚦 Project State` to the next Phase (or `complete`).

---

## Step 3 — Automatic Context-Aware Git, Branch & PR Router

1. **One Scoped Commit Per Verified Feature (Never `git add .` Across Unrelated Features):**
   - **Stealth Check First (`.git/info/exclude`):** Check if `.git/info/exclude` contains `.agents/` or `docs/` (Client/Company Stealth Mode).
     - **If Stealth Mode is active:** Stage **only** `<Touches-paths>` (never stage `AGENTS.md`, `CLAUDE.md`, `.agents/`, or `docs/`!). Use natural sentence commit summaries if `Context: Tangible` (e.g., `Add per-destination post type mapping to manual syncs`).
     - **If Tracked Mode (BFD-native / Solo / Open-Source):** Stage `<Touches-paths>`, `docs/specs/<NN-feature-slug>/`, and `docs/planning/roadmap.md` (plus any updated `docs/foundation/` file):
       ```bash
       git add <Touches-paths> docs/specs/<NN-feature-slug>/ docs/planning/roadmap.md
       git commit -m "feat(<NN-feature-slug>): <summary> (Req 1.1-X.Y)"
       ```
   - *(If shipping multiple features from a parallel wave, create **1 separate commit per feature** so each feature can be reviewed or reverted independently in `git log`!)*
2. **Automatic Branch & PR Routing (Zero Human Guessing):**
   Inspect `AGENTS.md` (`Context:`), `.git/info/exclude`, `.github/workflows/`, `CONTRIBUTING.md`, and `git shortlog -sn` to route automatically:
   - **Mode 1 — Company / Team / Client Repo** (`Context: Tangible` / `Team` / `Client`, `.git/info/exclude` active, or 2+ Git authors):
     - **Never merge or push directly to `main`/`master`.**
     - Push the feature branch (`git push -u origin feat/<NN-feature-slug>`) and run `gh pr create` (or output the ready-to-paste PR title & body below).
   - **Mode 2 — Open-Source / Public CI Repo** (`Context: Open-Source`, or `.github/workflows/` / `CONTRIBUTING.md` exists):
     - Push `git push -u origin feat/<NN-feature-slug>` and open a PR so GitHub Actions CI runs and `main` stays protected.
     - *(Open-Source Readiness Check: When transitioning a `Personal` repo to Open Source for the first time, verify `docs/foundation/prd.md` and `docs/planning/backlog.md` contain no private pricing/keys before flipping the repo visibility to Public.)*
   - **Mode 3 — Team-Ready Solo Mode (`Context: Personal`):**
     - You get **full Team/Open-Source discipline at Solo speed**: every feature or wave is already committed atomically on `feat/<NN-feature-slug>` (or `feat/wave-<N>`) with a complete PR Summary Card and `.github/workflows/ci.yml` ready.
     - Ask once at the end of the feature/wave (or follow user preference):
       - **Option A — 1-Second Fast Merge (`main`):** Run `git checkout main && git merge --no-ff feat/<NN-feature-slug>` (preserves the feature branch history in `git log --graph` like a merged PR!) and optionally `git push origin main`.
       - **Option B — Full GitHub PR Practice (`gh pr create`):** Push `git push -u origin feat/<NN-feature-slug>` and open the PR on GitHub so `.github/workflows/ci.yml` runs on the PR just like in a Team or Open-Source repo.
3. **Generate PR / Completion Summary (With Automatic Risk-Tier Tagging & Manual Testing Walkthrough):**
   - **Classify Risk Tier Automatically (Set by Changed Paths, Never Author Opinion):**
     - **`🟢 LOW RISK (Fast Human Skim)`:** Isolated UI/CRUD strictly inside `Touches`, zero schema/auth changes, 100% Level 1–3 checks pass.
     - **`🟡 MEDIUM RISK (Review UAT Walkthrough & API Contract)`:** New API routes, external third-party API calls, background/chunked jobs, or AI prompts (`src/lib/ai/`).
     - **`🔴 HIGH RISK — DEEP HUMAN REVIEW REQUIRED`:** Touches authentication/session guards, billing/payments/credits, RLS policies or DB migrations (`supabase/migrations/`), shared core contracts (`shared/types/`), or security/capability middleware. *(Always highlight the exact sensitive `file:line` ranges for the human reviewer!)*

```markdown
## 🚀 Shipped: [Feature Name] (`<NN-feature-slug>`)

### Summary & Risk Tier
- **Risk Tier:** `[🟢 LOW RISK / 🟡 MEDIUM RISK / 🔴 HIGH RISK — DEEP HUMAN REVIEW REQUIRED]` — `[1-line reason + exact sensitive file:line to inspect if 🔴/🟡]`
- **Spec Bundle:** `docs/specs/<NN-feature-slug>/`
- **Requirements Implemented:** `Req 1.1, 1.2, ...`
- **Verification:** [`docs/specs/<NN-feature-slug>/uat.md`](docs/specs/<NN-feature-slug>/uat.md) (✅ Passed + Blind Diff Audit Clean)

### Key Files Changed
- `[path/to/file]` — [1-line summary]

### 🧭 Manual Testing Walkthrough (How to Test Right Now)
- **Start / Open:** `[e.g., pnpm dev → http://localhost:3000/route or WP Admin → Menu]`
- **Happy Path:**
  1. `[Step 1 — exact page/button/input action]`
  2. `[Step 2 — submit / interact]` → **Expected:** `[exact UI & DB persistence result]`
- **Edge Case / Validation Check:**
  1. `[Trigger invalid input or empty state]` → **Expected:** `[exact error message / guard behavior]`

### 🧠 Self-Healing & Brain Updates
- **Project Brain (`AGENTS.md` / `decisions.md` / `issues.md`):** `[None needed / Summary of auto-applied guardrail]`
- **Master Brain (`shikamaru/feedback.md`):** `[None needed / YYYY-MM-DD | project | /ship | RULE | ...]`

### Next Unblocked Wave in `docs/planning/roadmap.md`
- Ready to build next: `[next-feature-slug(s)]`
```
