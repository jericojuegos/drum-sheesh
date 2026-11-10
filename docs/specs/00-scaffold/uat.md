# 🧪 User Acceptance Testing (UAT) & Verification: Foundation Scaffold (`00-scaffold`)

> **Phase / Wave:** `Phase 0 (Wave 0)` · **Status:** `[x]` Verified (`✅ PASS`)  
> **Spec Links:** [`tasks.md`](./tasks.md) · [`docs/foundation/architecture.md`](../../foundation/architecture.md) · [`docs/foundation/tech-stack.md`](../../foundation/tech-stack.md) · [`docs/foundation/decisions.md`](../../foundation/decisions.md)

---

## 1. Level 1 — Automated Build, Unit Tests & Manifest Permission Guard

| Check | Command | Status | Evidence / Proof |
| :--- | :--- | :---: | :--- |
| **Kotlin Compile & Typecheck** | `./gradlew compileDebugKotlin` | `[x]` | `BUILD SUCCESSFUL` across `:core`, `:ui`, `:platform`, `:feature`, `:app` (`task-115`) |
| **Unit & Architectural Boundary Tests** | `./gradlew testDebugUnitTest` | `[x]` | `CoreArchitectureBoundaryTest.kt` (`:core:test` & `:core:testDebugUnitTest` PASS) |
| **Merged Manifest Permission Allowlist** | `./gradlew checkMergedManifestPermissions` | `[x]` | `✅ Merged manifest permissions verified against allowlist: [android.permission.VIBRATE, android.permission.INTERNET, com.drumsheesh.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION]` |
| **GitHub Actions CI Workflow** | `.github/workflows/ci.yml` | `[x]` | `.github/workflows/ci.yml` created with JDK 17 Temurin & `./gradlew compileDebugKotlin testDebugUnitTest checkMergedManifestPermissions` |

---

## 2. Level 2 — ADR & Architectural Boundary Verification

| ID | Requirement / ADR | Expected Outcome | Status | Evidence / Proof |
| :--- | :--- | :--- | :---: | :--- |
| **ADR-001** | 5-Module "Portable Core, Platform Shell" (`:core`, `:ui`, `:platform`, `:feature`, `:app`) with zero Android imports in `:core` | `:core` applies `kotlin("jvm")` only and passes `CoreArchitectureBoundaryTest` | `[x]` | `core/build.gradle.kts:L1-L3`, `CoreArchitectureBoundaryTest.kt:L14-L42` (`PASS`) |
| **ADR-005** | Strict Manifest Permission Allowlist Guard (`checkMergedManifestPermissions`) | Merged debug manifest contains only approved permissions (`VIBRATE`, `INTERNET`) and rejects prohibited permissions (`RECORD_AUDIO`, `CAMERA`, `ACCESS_FINE_LOCATION`, `AD_ID`) | `[x]` | `build.gradle.kts:L12-L87`, `app/src/main/AndroidManifest.xml:L5-L7` (`PASS`) |

---

## 3. Level 3 — Visual & Theme Tokens Check (`docs/foundation/design-system.md`)

- [x] `StudioColors` defines Dark Studio `#0F0F1A` (`StageBackgroundDark`), `#FBBF24` (`ElectricAmber`), `#818CF8` (`AiCoachIndigo`), and Pad Category ring colors (`CymbalRingBrass`, `SnareRingCoral`, `TomRingCyan`, `KickRingViolet`).
- [x] `DrumSheeshTypography` uses `FontFamily.SansSerif` for UI labels and `FontFamily.Monospace` for `labelSmall` timing/BPM counters.
- [x] `MainActivity` is locked to `android:screenOrientation="sensorLandscape"` in `app/src/main/AndroidManifest.xml`.

---

## 🧭 Manual Testing Walkthrough

1. **Prerequisites & Local Setup:**
   - Ensure JDK 17 and Android SDK (`compileSdk 35`) are configured.
   - Run `./gradlew compileDebugKotlin testDebugUnitTest checkMergedManifestPermissions` from the repository root.
2. **Happy-Path Walkthrough:**
   - Launch `:app` (`MainActivity`) on an Android phone/tablet or emulator (`minSdk 26+`).
   - Confirm the activity opens in full-bleed landscape orientation (`sensorLandscape`) with the `#0F0F1A` Dark Studio stage background and `#FBBF24` Electric Amber title.
3. **Edge Cases & Boundary Validation:**
   - Add an unapproved permission (e.g., `android.permission.CAMERA` without `tools:node="remove"`) to `app/src/main/AndroidManifest.xml` and run `./gradlew checkMergedManifestPermissions` → verify the task fails with `FATAL: Merged manifest ... contains prohibited permission`.
