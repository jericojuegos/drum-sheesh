---
name: next
description: >-
  AI Product Researcher & Next-Wave Advisor (/next). Audits the current project state
  (AGENTS.md, docs/foundation/prd.md, docs/planning/roadmap.md, backlog.md, history.md),
  researches competitor features and user pain points on the web, and recommends the
  exact next action or top 3 highest-leverage features/waves to build next. Use when
  switching between multiple projects, deciding what to build next, expanding Phase 2+
  of a reverse-engineered product, or when the user invokes /next.
---

# 🧭 Skill: AI Product Researcher & Next-Wave Advisor (`/next`)

**Purpose:** Eliminate decision fatigue across multiple active projects. Whether you are returning to a project after days away or finishing a Phase and wondering *"What should we build or reverse-engineer next?"*, `/next` audits your project state, researches competitors/market gaps, and gives you a zero-guessing action plan.

---

## Step 1 — Instant Project State Audit (Where Are We Right Now?)

Read the project's Brain files in this exact order:
1. `AGENTS.md` (`Project State`, `Context`, `Master Brain`, `Anti-Hallucination List`)
2. `docs/planning/roadmap.md` (**Feature Specs Registry & Dependency Matrix** — count `[x]` Shipped, `[>]` Active, and `[ ]` Pending features)
3. `docs/foundation/prd.md` & `docs/planning/backlog.md` (Deferred Phase 2+ items and explicit Non-Goals)
4. `docs/planning/history.md` & `git status --short` / `git log -n 5 --oneline`

Classify the project into **one of two states**:
- **State A — Unfinished Current Phase (`[ ]` or `[>]` features remain in the active Phase):**
  - Identify the exact active (`[>]`) or next unblocked Wave (`[ ]`) in `docs/planning/roadmap.md`.
- **State B — Current Phase Complete (All features in the current Phase are `[x]`, or user explicitly asks *"What new features should we add/research?"*):**
  - Proceed to **Step 2 (Autonomous Competitor & Market Research)** to propose the next high-leverage Wave/Phase!

---

## Step 2 — Autonomous Competitor & Market Gap Research (When Planning Next Wave / Phase 2+ or Requested)

When in **State B** (or whenever the user asks what features to add next):
1. **Identify the Product Category & Reference Competitors:**
   - Extract the product's niche and reference competitor(s) from `docs/foundation/prd.md` (e.g., All-in-One WP Migration / UpdraftPlus / Duplicator for WP migration; Linear / Cal.com / Dub.co for SaaS, etc.).
2. **Run Targeted Web Research (`search_web` — if web search tool is available):**
   - Search for:
     - **Competitor Core Parity:** Must-have features users expect from the top 2–3 tools in this category in 2026.
     - **User Pain Points & Paywall Frustrations:** Common complaints, 1-star reviews, artificial limits, or UX friction in existing competitors that our project can solve 10x better.
     - **2026 AI / Automation Leverage:** Practical AI, automation, or developer-experience upgrades that fit the stack without violating the `AGENTS.md` Anti-Hallucination List.
   - *(If running in an offline sandbox without `search_web`, synthesize directly from `docs/foundation/prd.md`, `docs/planning/backlog.md`, and deep domain knowledge of the reference product category.)*

---

## Step 3 — Output the `/next` Executive Brief & 3 Ranked Options

Present a scannable, zero-fluff brief in this exact format:

```markdown
## 🧭 `/next` Project Brief: [Project Name]

### 1. Current Status Snapshot
- **Stage / Active Branch:** `[Stage from AGENTS.md]` · `[current git branch]`
- **Roadmap Progress:** `[X / Y features shipped in Phase N]`
- **Last Shipped:** `[Last completed feature slug & 1-line summary]`
- **Immediate Unfinished Work:** `[None — Phase N Complete! / List active [>] or next unblocked [ ] Wave]`

---

### 2. Recommended Next Move

#### 👉 If State A (Current Phase In Progress):
- **Ready Command to Run Right Now:**
  - Single Feature: `/build <NN-feature-slug>`
  - Full Remaining Wave/Phase (Hands-Off): `/goal /build Phase N` (or `/build all`)

#### 🚀 If State B (Current Phase Complete — Top 3 Next-Wave Proposals):
Based on `docs/foundation/prd.md`, `docs/planning/backlog.md`, and competitor/market analysis against **[Reference Competitors]**:

1. **Option A — Core Competitor Parity Wave (Recommended Next Phase)**
   - **Why It Matters:** `[1 sentence — closes the #1 table-stakes gap vs. competitor]`
   - **Proposed Feature Specs (`2–3 features`):**
     - `<NN-slug-1>`: `[1-line scope]`
     - `<NN-slug-2>`: `[1-line scope]`
   - **Estimated AI Build Time:** `[e.g., ~15–25 mins via /goal /build Phase N+1]`

2. **Option B — 10x Differentiator / UX / AI Upgrade Wave**
   - **Why It Matters:** `[1 sentence — solves a major competitor paywall/UX complaint or adds high-leverage automation]`
   - **Proposed Feature Specs (`1–3 features`):**
     - `<NN-slug-1>`: `[1-line scope]`
     - `<NN-slug-2>`: `[1-line scope]`

3. **Option C — Production Hardening, Distribution & Monetization Wave**
   - **Why It Matters:** `[1 sentence — prepares the repo for real users, WP.org/SaaS launch, E2E coverage, or billing/licensing]`
   - **Proposed Feature Specs (`1–3 features`):**
     - `<NN-slug-1>`: `[1-line scope]`

---
**Reply with:**
- **`A`**, **`B`**, or **`C`** (or a mix like **`A1 + B1`**) → I will automatically add the new Phase/Wave to `docs/planning/roadmap.md`, generate the `docs/specs/<NN-feature-slug>/` bundles via `/spec`, and give you the 1-line `/goal /build Phase N+1` command!
```

---

## Step 4 — Auto-Scaffold Selected Next Wave into `roadmap.md` & `docs/specs/`

As soon as the user replies with their choice (e.g., `A`, `B`, or `C`):
1. **Update `docs/planning/roadmap.md`:**
   - Add the selected features to the **Feature Specs Registry & Dependency Matrix** with proper `Wave`, `Depends On`, and non-overlapping `Touches (Isolation Zone)` paths.
   - Update the Mermaid Dependency Graph in `roadmap.md`.
2. **Generate Starter Spec Bundles (`/spec`):**
   - For each newly added feature, create `docs/specs/<NN-feature-slug>/{requirements.md, design.md, tasks.md, uat.md}` (and `design-reference/stitch-prompt.md` if `[FE]` UI is involved) following [`../spec/SKILL.md`](../spec/SKILL.md).
3. **Ready to Build:**
   - Finish by printing:
     > *"✅ Phase [N] specs and dependency waves are locked in `docs/planning/roadmap.md`. Run **`/goal /build Phase [N]`** (or **`/build all`**) to build the entire wave autonomously!"*
