# 🐛 Issues Log — Drum Sheesh

> **Location:** `docs/planning/issues.md`
> **Purpose:** Capture agent misbehaviors, workflow gaps, hallucinations, and
> brain bugs during development sessions. Do not stop to analyze — log and continue.
>
> **When done:** Review this file periodically to promote fixes to `master-brains`.
>
> **This is NOT for:**
> - Code bugs you fixed yourself → those go in the feature's Activity Log (`docs/specs/<feature-slug>/tasks.md`)
> - Reusable patterns → those go in `master-brains/feedback.md`
> - Blocked external deps → those go in the 🛑 Blocked section of `docs/specs/<feature-slug>/tasks.md`

---

## Issue Template

Copy this block for each new issue:

```markdown
---
**ISSUE-[N] — YYYY-MM-DD**
**Model:** [Gemini / Claude / Codex / Cursor / DeepSeek / Grok / Other]
**Severity:** 🔴 Blocking / 🟡 Annoying / 🟢 Minor
**Skill / Rule:** [e.g. .agents/skills/build/SKILL.md or .agents/rules/stack-rules.md]
**Stage:** [e.g. Phase 1 / Feature 01-drum-kit-stage]

**What should have happened:**
[What the brain says the agent should do]

**What actually happened:**
[Exact behaviour — be specific]

**Reproduction steps:**
[How to trigger this again]

**Status:** 🔴 Open
---
```

---

## Open Issues

---
**ISSUE-01 — 2026-11-10**
**Model:** Gemini 4 Argon / Gemini 3.8 Flash
**Severity:** 🔴 Blocking
**Skill / Rule:** `.agents/skills/build/SKILL.md` (Step 2 Scaffold / CI setup)
**Stage:** Phase 0 Scaffold CI verification

**What should have happened:**
GitHub Actions workflow runs `./gradlew compileDebugKotlin testDebugUnitTest checkMergedManifestPermissions` cleanly on `ubuntu-latest`.

**What actually happened:**
On Windows development machines, `gradlew` is checked into git with `100644` non-executable permissions by default. When checked out on Linux in GitHub Actions, `./gradlew` fails with `Permission denied` (exit code 126).

**Reproduction steps:**
Create an Android scaffold on Windows, push to GitHub, and run CI on `ubuntu-latest` without `git update-index --chmod=+x gradlew` or a `chmod +x gradlew` step.

**Fix Applied:**
1. Ran `git update-index --chmod=+x gradlew` to set git index mode to `100755`.
2. Added `chmod +x gradlew` step in `.github/workflows/ci.yml`.
3. Updated `.agents/skills/build/examples/github-ci.yml.template`.

**Status:** 🟢 Resolved
---

---

---

## Resolved Issues

*(Issues moved here after fix confirmed in master-brains)*
