---
name: verify
description: >-
  Run Level 1 (Automated build/types/tests), Level 2 (EARS functional smoke checks),
  and Level 3 (Visual fidelity against design-reference/*.png & Stack Hard-Failure review)
  verification against docs/specs/<NN-feature-slug>/uat.md, or triage an external security/code
  audit report (docs/audits/*.md) with automatic ALREADY FIXED detection and right-sized routing
  (Direct Fix vs. Remediation /spec). Use after completing feature tasks, when auditing security
  reports, or when the user invokes /verify.
---

# 🧪 Skill: Feature Verification & Hard-Failure Review (`/verify`)

This skill verifies a completed feature against its EARS acceptance criteria (`requirements.md`), technical design (`design.md`), visual mockups (`design-reference/`), UAT checklist (`uat.md`), and stack rules (`.agents/rules/stack-rules.md`).

---

## Step 1 — Load the Feature Spec & UAT Checklist

1. Read `docs/specs/<NN-feature-slug>/requirements.md`, `design.md`, `tasks.md`, and `uat.md`.
2. If `uat.md` does not exist yet, create it from [`../spec/resources/uat.md.template`](../spec/resources/uat.md.template).

---

## Step 2 — Level 1: Automated Build, Unit Tests, Playwright E2E & Stack Hard-Failure Audit

1. **Run Terminal Verification, Unit Tests & Golden-Path E2E:**
   - **Next.js / TypeScript:** `pnpm tsc --noEmit && pnpm test --run && pnpm build` (verifying all unit tests in `src/features/<domain>/__tests__/` pass).
   - **WordPress / PHP:** `php -l` across all plugin/theme PHP files (plus `vendor/bin/phpunit` for `tests/Unit/`).
   - **Python / FastAPI:** `uv run ruff check . && uv run pytest`.
   - **Web3 / Foundry:** `forge build && forge test -vvv` + `pnpm tsc --noEmit && pnpm test --run`.
   - **Playwright E2E Smoke Suite (If `e2e/*.spec.ts` exists & not inside a parallel subagent wave):** Run `pnpm test:e2e` (`npx playwright test`) to verify Golden-Path user journeys in a real headless browser. When writing/running E2E specs for a feature, save **0-token visual evidence screenshots** (`await page.screenshot({ path: 'docs/specs/<NN-feature-slug>/evidence/01-happy-path.png' })`) so the human reviewer can inspect the real rendered UI without spending LLM vision tokens. *(If running inside a parallel Mode B subagent, skip starting the E2E server inside the subagent — the Main Agent runs `pnpm test:e2e` once at the Inter-Wave Checkpoint.)*
   - **AI Prompt / LLM Evals (If feature touches `src/lib/ai/prompts/` or LLM logic):** Confirm co-located AI eval tests (`src/lib/ai/__tests__/*.eval.test.ts` or `tests/evals/test_*_eval.py`) pass Tier 1 deterministic prompt-injection & schema contract checks during `pnpm test --run` / `uv run pytest` (and `RUN_AI_EVALS=1` live golden dataset evals when an API key is configured).
2. **Audit Against `.agents/rules/stack-rules.md` (Pre-Commit / Hard-Failure Checklist) & `AGENTS.md` (With Fresh-Context Blind Diff Audit):**
   - Check every file touched by this feature (`design.md` Section 2) for:
     - Layer boundary violations (e.g., cross-feature imports `features/[a]/` → `features/[b]/`, business logic in `page.tsx` or `templates/`)
     - Security violations (e.g., missing `import 'server-only'`, missing Zod `safeParse`, missing RLS, or in WordPress: missing `ABSPATH` guard, unprepared `$wpdb` SQL, missing nonce/capability check, unescaped template output)
     - Anti-Hallucination violations (anything forbidden in `AGENTS.md` or `requirements.md` Non-Goals).
   - **Fresh-Context "Blind Diff" Subagent Audit (Eliminates Author Confirmation Bias):**
     - When an agent reviews code in the *same* session where it wrote the code, it suffers from author bias.
     - If running in a main session where subagent tools exist (`invoke_subagent` with `TypeName: "research"` in Antigravity, or the read-only `task` tool in Claude Code / OpenCode / Codex) and the feature modified 3+ code files (or touches Auth/Billing/DB/Security), spawn a **fresh read-only reviewer subagent** with zero author memory:
       > *"Read `docs/specs/<NN-feature-slug>/{requirements,design}.md`, `.agents/rules/stack-rules.md`, `docs/foundation/decisions.md`, and inspect the feature's changed files (`<Touches>`). Report ONLY provable `[P0 — Blocker]` or `[P1 — Defect]` issues with exact `file:line` and trigger/exploit path. Suppress all `P2` style nitpicks. If zero P0/P1 exist, reply `✅ 0 P0/P1 Findings`."*
     - Fix any real `P0`/`P1` defect surfaced by the blind diff reviewer (adding a regression unit test per `execution-protocol.md` Section 6.1) and re-run Level 1 verification before proceeding. *(Skip spawning a nested subagent if already running inside a Mode B feature subagent — the Main Agent audits at the Wave checkpoint.)*

---

## Step 3 — Level 2: EARS Functional & Error-Matrix Verification (With Mandatory Evidence Column)

Verify each numbered requirement (`Req 1.1`, `Req 1.2`, etc.) and each row of the **Error Handling Matrix** in `design.md`:
- Confirm happy-path behavior (`WHEN ... THE System SHALL ...`).
- Confirm error-path behavior (`IF ... THEN THE System SHALL ...`) returns the exact expected status code, writes zero partial DB records on failure, and preserves user input in the UI.
- **Populate the `Evidence / Proof` Column (Zero Unbacked `[x]`):** Update each row in `docs/specs/<NN-feature-slug>/uat.md` (`[x]` Pass or `[!]` Fail) AND fill in the **`Evidence / Proof`** column with the exact automated test citation (`feature.test.ts:L24 PASS`), CLI/API verification output, or screenshot path (`./evidence/01-happy-path.png`). Never mark a row `[x]` without concrete evidence.

---

## Step 4 — Level 3: Visual Fidelity (`design-reference/`), Manual Walkthrough & Opt-In `flash` Browser QA

1. Check `docs/specs/<NN-feature-slug>/design-reference/`:
   - If reference screenshots (`*.png` / `*.jpg`) exist, read them with `read_file` and compare against the implemented UI components (or `evidence/*.png` screenshots) to confirm layout, sections, fields, and visual hierarchy match.
2. Confirm UI components use design tokens from `docs/foundation/design-system.md` (no arbitrary hardcoded hex colors).
3. Confirm loading states, empty states, and double-submit prevention are in place.
4. **Populate / Finalize the `## 🧭 Manual Testing Walkthrough` in `docs/specs/<NN-feature-slug>/uat.md`:**
   - Replace any template placeholders in `uat.md`'s `## 🧭 Manual Testing Walkthrough` section with the **exact as-built details** from the code:
     - **Prerequisites & Local Setup:** Exact dev server command, entry URL (`http://localhost:3000/...` or WP Admin menu path), and required login/seed data.
     - **Happy-Path Walkthrough:** Numbered, click-by-click steps referencing real UI button labels, input field names, and expected visual/DB outcomes.
     - **Edge Cases & Error Validation:** 1–2 concrete manual checks for validation errors, empty states, or unauthorized access.
5. **Opt-In Cost-Guarded Agentic Browser QA (Never Burn Frontier Tokens Unprompted):**
   - **Why Opt-In + `flash`:** Live interactive browser clicking (DOM snapshots + screenshots on every step) consumes high token volume if run automatically on a Frontier model.
   - **Default Behavior:** Do **not** auto-launch a live interactive browser session unless the user explicitly passed `/verify --browser`. Instead, if UI files were touched and no Playwright `evidence/*.png` screenshots were captured in Step 2, ask the user in the Verification Report:
     > *"💡 **Optional Agentic Browser QA:** Want me to execute the `Manual Testing Walkthrough` in a live browser and save proof screenshots to `docs/specs/<NN-feature-slug>/evidence/`? (Spawns a lightweight `flash` subagent to avoid burning Frontier tokens, or you can test manually using the walkthrough in `uat.md`)."*
   - **When Approved (or `/verify --browser` Invoked):**
     - Spawn a `self` subagent with **`Model: 'flash'`** (unless the user explicitly requests Frontier) and pass it **only** `requirements.md`, `uat.md` (`## 🧭 Manual Testing Walkthrough`), and the local URL.
     - The `flash` QA subagent executes the click-by-click walkthrough via browser tools / Playwright CLI, checks `console.error` and network `4xx/5xx`, saves happy-path & error-state screenshots to `docs/specs/<NN-feature-slug>/evidence/*.png`, and updates the `Evidence / Proof` column in `uat.md`.
6. Mark `## 🧪 Feature Verification & UAT Gate` complete (`[x]`) in `docs/specs/<NN-feature-slug>/tasks.md`.

---

## Step 5 — Evidence-Only Review Gate, External Audit Router (`docs/audits/`) & Session Self-Healing

To prevent multi-model review ping-pong (Gemini vs. Claude vs. Codex), handle external security/code audit files (`docs/audits/*.md`), and capture lessons when the user runs `/verify`:

1. **Evidence-Only Severity Filter (Zero Stylistic Bikeshedding):**
   - Only report **`[P0 — Blocker]`** (provable bug, security/auth/RLS/nonce flaw, broken EARS requirement, or `stack-rules.md` Hard Failure) and **`[P1 — Defect]`** (unhandled `design.md` Error Matrix case or race condition) with exact `file:line` and a concrete trigger path.
   - Suppress **`[P2 — Style/Nitpick]`** opinions (DRY vs. inline preferences, hypothetical scale, or decisions already settled in `docs/foundation/decisions.md` or `design.md`).
2. **Arbiter Mode & External Audit Router (When Passed `docs/audits/*.md` or Pasted External Review):**
   - **Step 2a — Live Codebase 3-Bucket Triage (Never Trust Stale Reports Blindly):**
     Inspect the actual live codebase, `design.md`, `docs/foundation/decisions.md`, and `.agents/rules/stack-rules.md` for every finding in the audit and classify each into one of 3 buckets:
     - **`✅ ALREADY FIXED`:** The live codebase already contains the fix or guard (cite exact `file:line` proof). Do **not** generate tasks for already-fixed items.
     - **`🛡️ REJECT (P2 / False Positive / Settled Decision)`:** Subjective style preference, impossible trigger path, or already guarded upstream → do **not** modify code; output a 1-line technical rebuttal citing the exact `file:line` or `decisions.md` entry.
     - **`🔴 OPEN P0 / P1 (Actionable Defect / Vulnerability)`:** Verified still-open bug, exploit path, or spec/stack-rule violation in the current code (cite exact `file:line`).
   - **Step 2b — Automatic Right-Sized Remediation Router (Direct Fix vs. New `/spec`):**
     Count the verified **`🔴 OPEN P0 / P1`** items and their blast radius, then automatically route:
     - **Path A — Direct Fix Inside `/verify` (`<= 3 files` touched AND zero DB schema / Auth architecture / Shared contract changes):**
       Fix the open `P0/P1` items immediately, add regression unit tests (`execution-protocol.md` §6.1), re-run Level 1 terminal verification, and log the fixes in the active `tasks.md` Activity Log (or audit file summary).
     - **Path B — Auto-Route to Remediation `/spec` (`4+ files` touched OR requires DB schema / Auth flow / Cross-module architecture changes):**
       Do **not** make unplanned multi-file edits inline. Output the triage table (`✅ ALREADY FIXED`, `🛡️ REJECT`, `🔴 OPEN P0/P1`) and immediately generate (or ask once to confirm generating) a dedicated remediation spec bundle (`docs/specs/<NN-security-remediation>/`) via `../spec/SKILL.md` containing **only** the verified `🔴 OPEN P0 / P1` findings.
3. **Automatic Session Self-Healing & Brain-Gap Routing:**
   - Scan any bugs, build failures, or user corrections that occurred during this feature session and automatically route them without asking the user where they belong:
     - **Project-Specific Rule/Edge Case** → Auto-update `AGENTS.md`, `docs/foundation/decisions.md`, or `design.md`.
     - **Agent Misbehavior / Workflow Gap** → Auto-append to `docs/planning/issues.md`.
     - **Reusable Stack Rule / Template Gap** → Emit a `🧠 Master Brain Signal` line in the Verification Report below for `shikamaru/feedback.md`.

---

## Step 6 — Output Review Summary

Output a clear verification report:

```markdown
## 🧪 Verification Report: `<NN-feature-slug or docs/audits/report.md>`
**Result:** ✅ PASS / ❌ FAIL / 🔀 ROUTED TO `/spec`

- **Level 1 (Build, Types & Tests):** [PASS/FAIL]
- **Stack Hard-Failure Audit:** [PASS/FAIL]
- **Level 2 (EARS Criteria & Evidence Column):** [X/Y Verified with test/CLI/screenshot citations in `uat.md`]
- **Level 3 (Visual Match vs `design-reference/` & Tokens):** [PASS/FAIL]
- **Evidence Artifacts (`evidence/`):** [Playwright E2E screenshots saved / Unit+API citations in `uat.md`]
- **P0 / P1 Defects Found:** [0 — Ready to Ship / List with file:line + trigger path]
- **External Audit Triage (If applicable):** [Already Fixed: X · Rejected (P2/False Positive): Y · Open P0/P1: Z → Direct Fix (<=3 files) OR Routed to `docs/specs/NN-security-remediation/` (4+ files)]
- **🧠 Self-Healing & Brain Updates:** [None needed / Updated `AGENTS.md` / Logged `issues.md` / `shikamaru/feedback.md` signal]
```

If **✅ PASS**:
- If UI files were modified and live Browser QA hasn't run yet, ask: *"Want me to run **Opt-In Agentic Browser QA** (via a lightweight `flash` subagent) to capture `evidence/*.png` screenshots, or proceed directly to **`/ship`**?"*
- Otherwise, prompt the user to run **`/ship`** (`../ship/SKILL.md`) to update `roadmap.md` and prepare the commit/PR.
