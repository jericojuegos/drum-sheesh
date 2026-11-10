---
trigger: always_on
globs: "**/*.kt, **/*.kts, **/AndroidManifest.xml"
description: Kotlin 2.x + Jetpack Compose + Material 3 + Android Architecture Components (Portable Core, Platform Shell) boundaries, coding standards, and pre-commit checklist
---

# 📱 Stack Rules: Native Android (Kotlin + Jetpack Compose)

---

## 1. Folder Responsibilities & Layer Boundaries (5-Module Layout)

This stack uses a **Layered Modular Architecture** with a pure-Kotlin portable core and an Android platform shell.

### Module & Package Map

| Module / Package | What Lives Here | What NEVER Lives Here |
|---|---|---|
| `:app` | `Application` class, `MainActivity`, Root Navigation host, `AppContainer` (manual DI composition root), app-level manifest | Business logic, direct hardware logic, raw SQL/database calls |
| `:core` | Pure Kotlin domain models, calculation engines, state machines, interfaces/ports (`Clock`, `PreferencesRepository`, domain ports) | **ANY** Android import (`android.*`, `androidx.*`), Compose UI, CameraX, ML Kit |
| `:ui` | Compose Theme, Material 3 tokens, generic reusable Canvas renderers, UI utility modifiers, preview mocks | Hardware/platform drivers, network calls, raw SQLite/File I/O |
| `:platform` | Android platform implementations of `:core` ports (CameraX, Sensors, Haptics, DataStore, Bluetooth) | Presentation UI composables, application routing, business state machines |
| `:feature` | Feature screens, ViewModels, UDF `UiState` / `UiEvent` definitions, screen-level Composables | CameraX frames, direct platform sensor listeners (must consume `:core` ports) |

---

## 2. Mandatory Android & Compose Standards

1. **Pure Kotlin Core (Zero Android Imports):**
   - The `:core` module must apply `kotlin("jvm")` (or `kotlin("multiplatform")`), never `com.android.library`.
   - Any commit adding an Android dependency to `:core` is a hard architectural violation.
2. **Unidirectional Data Flow (UDF) in ViewModels:**
   - ViewModels expose a single immutable `StateFlow<UiState>`:
     ```kotlin
     private val _uiState = MutableStateFlow(InitialUiState)
     val uiState: StateFlow<UiState> = _uiState.asStateFlow()
     ```
   - UI emits discrete events (`fun onEvent(event: UiEvent)`) to the ViewModel.
   - Composables must be stateless whenever practical, receiving state and hoisting event lambdas.
3. **Compose Performance & Allocation Rules:**
   - **No per-frame object allocation** inside `Canvas`, `drawBehind`, or `onDrawWithContent`. Allocate `Path`, `Paint`, and `Matrix` once or in remember/derived blocks.
   - Use `derivedStateOf` for values computed from frequently changing states.
   - Always pass keys to `items()` in Lazy lists.
4. **Lifecycle-Aware Hardware & Background Operations:**
   - Any CameraX `ImageAnalysis`, sensor listener, or audio recorder MUST bind to `LifecycleOwner` or listen to `DefaultLifecycleObserver`.
   - Never keep hardware active in `onStop` unless backed by an approved, foreground-service with ongoing notification.
5. **Privacy & Manifest Hygiene:**
   - Only declare permissions explicitly documented in `docs/foundation/tech-stack.md`.
   - The `tools:node="remove"` directive is reserved for stripped transitive permissions, and must be guarded by automated CI manifest verification tasks.
   - No camera frames, raw audio, or biometric data may be written to disk (`filesDir`, `cacheDir`, `externalFilesDir`, `SharedPreferences`, SQLite).

---

## 3. Pre-Commit & Hard-Failure Checklist (Verify Before Marking `[x]`)

- [ ] `./gradlew compileDebugKotlin` succeeds with zero compiler warnings or errors
- [ ] `./gradlew testDebugUnitTest` passes for all domain and feature tests
- [ ] `:core` module contains zero imports matching `android.*` or `androidx.*`
- [ ] Merged manifest contains only approved allowlist permissions (run `./gradlew checkMergedManifestPermissions`)
- [ ] No allocations in Canvas render loops
- [ ] No hardcoded secrets, plain HTTP URLs, or unvetted third-party SDK dependencies
- [ ] All `AGENTS.md` Critical Constraints satisfied

