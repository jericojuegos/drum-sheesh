# ✅ Feature Tasks: Foundation Scaffold (`00-scaffold`)

> **Phase / Wave:** `Phase 0 (Wave 0)` · **Branch / Owner:** `main` · **Status:** `[x]` Complete  
> **Depends On:** `None` · **Touches:** `settings.gradle.kts, build.gradle.kts, gradle.properties, core/, ui/, platform/, feature/, app/, .github/workflows/ci.yml`  
> **Spec Links:** [`docs/foundation/architecture.md`](../../foundation/architecture.md) · [`docs/foundation/tech-stack.md`](../../foundation/tech-stack.md) · [`docs/foundation/design-system.md`](../../foundation/design-system.md)

---

## 🔄 Implementation Tasks

### 1. Multi-Module Gradle Setup & Manifest Permission Guard (`⚡ BATCH`: Root Build, 5 Modules & Permission Guard)
- [x] **1.1** `[BE]` Initialize root Gradle Kotlin DSL (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`) with 5 modules (`:core`, `:ui`, `:platform`, `:feature`, `:app`) and register the `checkMergedManifestPermissions` verification task enforcing allowlist `["android.permission.VIBRATE", "android.permission.INTERNET"]`
  - Ensure `:core` applies `kotlin("jvm")` and `kotlin("plugin.serialization")` only (zero Android plugins/dependencies)
  - _Requirements: ADR-001, ADR-005_
- [x] **1.2** `[BE]` Create `:core` base domain contracts (`com.drumsheesh.core.model.DrumPadId`, `DrumKitId`, `ClockPort`) and `:core` architectural boundary test (`CoreArchitectureBoundaryTest.kt`) verifying zero `android.*` or `androidx.*` imports inside `:core`
  - _Requirements: ADR-001_
- [x] **1.3** `[FE]` Create `:ui` Dark Studio theme (`Color.kt`, `Type.kt`, `StudioTokens.kt`, `DrumSheeshTheme.kt`) matching `docs/foundation/design-system.md` (`#0F0F1A` stage background, `#FBBF24` Electric Amber primary, `#818CF8` AI Coach accent)
  - _Requirements: ADR-001_
- [x] **1.4** `[FE]` Create `:platform` placeholder wiring, `:feature` root host shell, and `:app` (`AndroidManifest.xml` locked to `sensorLandscape`, `DrumSheeshApplication.kt`, `AppContainer.kt`, `MainActivity.kt`) plus `.github/workflows/ci.yml` from `.agents/skills/build/examples/github-ci.yml.template`
  - _Requirements: ADR-001, ADR-005_
- [x] **1.5** `[REVIEW]` **Phase 0 Scaffold Checkpoint:** Run `./gradlew compileDebugKotlin testDebugUnitTest checkMergedManifestPermissions` — must pass with zero errors (triggers Phase 0 commit on `main` and updates `AGENTS.md` stage to `scaffolded`)

---

## 🔀 Task Dependency Graph (Intra-Feature Waves)

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "1.4"] },
    { "id": 1, "tasks": ["1.5"] }
  ]
}
```

---

## 🧪 Feature Verification & UAT Gate
- [x] **Level 1 (Automated Build & Unit Tests):** `./gradlew compileDebugKotlin testDebugUnitTest checkMergedManifestPermissions` passes with zero errors
- [x] **Level 2 (Boundary Check):** `:core` module contains zero `android.*` or `androidx.*` imports

---

## 🛑 Blocked / Waiting
*(None)*

---

## 📝 Feature Activity Log

| Date | Task | Action & Files Touched |
| :--- | :--- | :--- |
| 2026-11-10 | Init | Phase 0 `00-scaffold` tasks created during `/brainstorm`. |
| 2026-11-10 | 1.1–1.5 | Built 5-module Android + Pure-Kotlin `:core` scaffold (`settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `core/`, `ui/`, `platform/`, `feature/`, `app/`, `.github/workflows/ci.yml`) and verified `./gradlew compileDebugKotlin testDebugUnitTest checkMergedManifestPermissions` passes with zero warnings/errors. |
