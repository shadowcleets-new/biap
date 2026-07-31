# HAIKU 4.5 OPERATION MANUAL — OPTION A
## Enable Material 3 Expressive by moving to the public-API Compose train

**Author:** Opus 4.8 (Principal Android Architect)
**Executor:** Claude Haiku 4.5 — mechanical execution only. Do not improvise versions or APIs.
**Repo root:** `C:\Users\acer\projects\biap`
**Base state:** branch `master` at commit `991ccca` (standard Material 3, all 30 bugs fixed, 14 tests green, debug+release building).
**Depends on:** the previously-completed `HAIKU_OPERATION_MANUAL.md`. This manual only adds the Expressive uplift that was blocked before.

---

## 0. WHY THIS MANUAL EXISTS (read once, then execute)

The first manual assumed Compose BOM `2026.03.01` exposed Material 3 Expressive publicly. It does not: that BOM pins `material3:1.4.0`, and in **1.4.0 the entire Expressive surface is `internal` or absent** (`MaterialExpressiveTheme`, `MotionScheme.expressive()/standard()`, `ExperimentalMaterial3ExpressiveApi`, `titleLargeEmphasized` getter are all `internal`; `LoadingIndicator` and `LinearWavyProgressIndicator` don't exist yet). So GROUP C was reverted to standard Material 3.

Option A fixes that at the dependency level: move the Compose stack to the release train where those six symbols are **public**.

### VERIFIED FACTS (empirically confirmed against the live Gradle cache + a probe compile — not assumed)

| Fact | Value | How it was proven |
|------|-------|-------------------|
| First material3 with a **public** Expressive surface | `1.5.0-alpha23` (latest published; no `1.5.0` stable exists yet) | Downloaded the 1.5.0-alpha23 sources jar; every one of the six symbols is declared **without** `internal` |
| Compose stack material3 1.5.0-alpha23 requires | `androidx.compose.*:1.12.0-alpha03` (foundation, ui, runtime, animation, material-ripple) | Read material3-android-1.5.0-alpha23.pom |
| Kotlin baseline material3 1.5.0-alpha23 was built against | `kotlin-stdlib:2.1.20` | Read the same POM — a **patch** bump from the project's 2.1.0, NOT the 2.3.20 the notes implied |
| The combination compiles | ✅ `compileDebugKotlin` **BUILD SUCCESSFUL** | A probe composable exercising all six symbols was compiled on a throwaway branch with the exact version set below; the entire existing codebase compiled against it too |

### ⚠️ RISK DISCLOSURE — the owner has accepted this by choosing Option A
1. **`material3:1.5.0-alpha23` is an ALPHA.** Its Expressive API can change between alpha builds. This is the only way to get the public API today (there is no 1.5.0 stable).
2. **The whole Compose stack moves to `1.12.0-alpha03`** — this affects **every screen**, Navigation 3, Coil, and lifecycle-compose interop, not just the Expressive widgets. A full regression pass (TASK-A6) is mandatory, not optional.
3. **Only `compileDebugKotlin` is proven.** `assembleRelease` (R8 minify, which is ON) is NOT yet verified against the alpha artifacts and may need new `-dontwarn` lines. TASK-A5 handles this.
4. If any step fails in a way not covered here, STOP and report — do not invent a different version or API.

---

## EXECUTION PROTOCOL
Execute TASK-A0 → TASK-A7 in order. After the version bump (TASK-A1) the first compile will re-download ~40 MB of alpha artifacts and take several minutes — this is expected. Circuit breaker: max 2 self-correction attempts per task; on the 2nd failure STOP and report (do **not** `git checkout` — work on the branch from TASK-A0 so rollback is just a branch switch).

---

## TASK-A0 — Branch (MANDATORY FIRST)
Do this work on a branch so the alpha migration is trivially reversible and never risks `master`.
```
git checkout -b feature/m3-expressive
```

---

## TASK-A1 — Version catalog: move to the Expressive train
**File:** `gradle/libs.versions.toml`. Two exact edits. These versions are VERIFIED — do not change a character.

**Edit 1 — `[versions]` block.** OLD:
```
androidxComposeBom = "2026.03.01"
androidxTest = "1.7.0"
```
NEW:
```
androidxComposeBom = "2026.03.01"
composeAlpha = "1.12.0-alpha03"
material3Alpha = "1.5.0-alpha23"
androidxTest = "1.7.0"
```

**Edit 2 — bump Kotlin (patch bump; the compose-compiler plugin follows it automatically via `version.ref = "kotlin"`).** OLD:
```
kotlin = "2.1.0"
```
NEW:
```
kotlin = "2.1.20"
```

**Edit 3 — pin the Compose UI group + material3 to the alpha train (overrides the BOM's stable 1.4.0/1.10.x for exactly these artifacts; the BOM still manages everything else).** OLD:
```
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3"}
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui"}
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview"}
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4"}
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling"}
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest"}
```
NEW:
```
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3", version.ref = "material3Alpha"}
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui", version.ref = "composeAlpha"}
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview", version.ref = "composeAlpha"}
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4", version.ref = "composeAlpha"}
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling", version.ref = "composeAlpha"}
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest", version.ref = "composeAlpha"}
```
Do NOT touch `androidx-material-icons-extended` (it stays BOM-managed — verified to resolve cleanly). Do NOT change AGP (`9.2.1`) or the Gradle wrapper (`9.4.1`) — both verified compatible.

**Checkpoint:** run `.\gradlew.bat compileDebugKotlin`. It must reach BUILD SUCCESSFUL (downloads the alpha train first). The existing standard-M3 code compiles against the alpha train — verified. If it fails here, the failure is dependency resolution, not code; STOP and report the exact Gradle error.

---

## TASK-A2 — `theme/Theme.kt`: standard theme → Expressive theme
This file uses **explicit imports** (not a wildcard), so imports must change. Three exact edits.

**Edit 1 — imports.** OLD:
```
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
```
NEW:
```
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
```
(`MaterialTheme` import is removed; the three Expressive imports are added. `PerformanceProfile` and `rememberPerformanceProfile` are already imported in this file.)

**Edit 2 — opt-in + Expressive theme.** OLD:
```
@Composable
fun BiapTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color stays OFF to protect the Contemporary Heritage brand identity.
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
```
NEW:
```
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BiapTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color stays OFF to protect the Contemporary Heritage brand identity.
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  // M3 Expressive motion app-wide; calmer standard scheme on battery-saver / low-core devices.
  val motionScheme =
    if (rememberPerformanceProfile() == PerformanceProfile.LOW_POWER) MotionScheme.standard()
    else MotionScheme.expressive()

  MaterialExpressiveTheme(
    colorScheme = colorScheme,
    motionScheme = motionScheme,
    typography = Typography,
    content = content
  )
}
```

**Checkpoint:** `.\gradlew.bat compileDebugKotlin` → BUILD SUCCESSFUL.

---

## TASK-A3 — `ui/main/MainScreen.kt`: expressive indicators + emphasized title
This file wildcard-imports `androidx.compose.material3.*`, so **no new imports are needed** — `LoadingIndicator`, `LinearWavyProgressIndicator`, and `ExperimentalMaterial3ExpressiveApi` all resolve. Five exact edits. The `profile` variable already exists (line ~140).

**Edit 1 — opt-in.** OLD:
```
@OptIn(ExperimentalMaterial3Api::class)
```
NEW:
```
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
```

**Edit 2 — emphasized portfolio title.** OLD:
```
                title = { Text("Product Portfolio", style = MaterialTheme.typography.titleLarge) },
```
NEW:
```
                title = { Text("Product Portfolio", style = MaterialTheme.typography.titleLargeEmphasized) },
```

**Edit 3 — expressive sync spinner (HIGH_FIDELITY branch only).** OLD:
```
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
```
NEW:
```
                                } else {
                                    LoadingIndicator(modifier = Modifier.size(28.dp))
                                }
```

**Edit 4 — expressive loading state.** OLD:
```
            MainScreenUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
```
NEW:
```
            MainScreenUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (profile == PerformanceProfile.LOW_POWER) {
                        CircularProgressIndicator()
                    } else {
                        LoadingIndicator()
                    }
                }
            }
```

**Edit 5 — wavy sync bar.** OLD:
```
                    if (isSyncing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
```
NEW:
```
                    if (isSyncing) {
                        if (profile == PerformanceProfile.LOW_POWER) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
```

**Checkpoint:** `.\gradlew.bat compileDebugKotlin` → BUILD SUCCESSFUL.

---

## TASK-A4 — `ui/design/DesignDetailScreen.kt`: expressive loader
Wildcard-imports `androidx.compose.material3.*` — no new imports. Two exact edits.

**Edit 1 — opt-in.** OLD:
```
@OptIn(ExperimentalMaterial3Api::class)
```
NEW:
```
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
```

**Edit 2 — expressive loader (there is exactly one `CircularProgressIndicator()` in this file).** OLD:
```
                Box(modifier = Modifier.padding(paddingValues).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
```
NEW:
```
                Box(modifier = Modifier.padding(paddingValues).fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
```

**Checkpoint:** `.\gradlew.bat compileDebugKotlin` → BUILD SUCCESSFUL.

---

## TASK-A5 — Full build & test verification
Run in order; all must pass:
```
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```
- `test` + `assembleDebug` are expected green (compile already verified).
- `assembleRelease` (R8 minify is ON) is the one NOT pre-verified. If it fails ONLY with an R8 "missing class" / `can't find referenced` warning pointing at an `androidx.compose.*` alpha class, add the exact `-dontwarn <class-pattern>` line R8 prints to `app/proguard-rules.pro` and rerun. That is the single permitted deviation. Any other failure → STOP and report.

---

## TASK-A6 — On-device smoke test (MANDATORY — the alpha stack touches every screen)
Install `app-debug.apk` on an emulator/device and confirm, in order:
1. Splash → portfolio grid renders (no crash from the alpha runtime).
2. Portfolio title "Product Portfolio" renders in the **emphasized** type style.
3. Sign in → during sync the top bar shows the **shape-morphing LoadingIndicator** and a **LinearWavyProgressIndicator** appears above the search bar.
4. Enable Battery Saver → re-enter: indicators fall back to circular/linear and motion calms (LOW_POWER path).
5. Open a design → its loading state shows the LoadingIndicator; detail renders; price shows `₹1499.5`-style formatting (regression check on the earlier fixes).
6. Add a design, edit a design, delete a design — no regressions from the Compose-runtime bump.
7. Rotate the device on the detail edit screen — no crash (state-restore regression check).

If any screen crashes or renders broken, the alpha Compose train has an incompatibility: STOP, capture the logcat stack trace, and report. Do not attempt blind fixes.

---

## TASK-A7 — Commit (only after A5 green AND A6 clean)
```
git add -A
git commit -m "feat(ui): adopt Material 3 Expressive via compose 1.12.0-alpha03 / material3 1.5.0-alpha23"
```
Then append a Chronos entry to `CHANGELOG.md` (per project convention) recording the version bump, the six now-public Expressive symbols, and the alpha-risk note. Leave the branch `feature/m3-expressive` for the owner to merge — do NOT merge to `master` yourself.

---

## PHASE 9 GUARDRAILS (apply to every task above)
1. **No version invention.** The only versions you may write are the four verified ones: Kotlin `2.1.20`, `composeAlpha = 1.12.0-alpha03`, `material3Alpha = 1.5.0-alpha23`, BOM stays `2026.03.01`. If you feel tempted to "try a newer/stable material3," STOP — there is no 1.5.0 stable; report instead.
2. **No new imports in the two screens.** They wildcard-import `material3.*`. Only `Theme.kt` gets import edits (exactly the three specified).
3. **No truncation, no `// TODO`, no `// unchanged`.** OLD blocks must match byte-for-byte on non-blank lines; if one doesn't match, STOP and report the actual bytes found.
4. **Nomenclature lock:** `MaterialExpressiveTheme`, `MotionScheme.expressive()`, `MotionScheme.standard()`, `ExperimentalMaterial3ExpressiveApi`, `titleLargeEmphasized`, `LoadingIndicator`, `LinearWavyProgressIndicator` — spelled exactly.
5. **Rollback:** everything is on `feature/m3-expressive`. To abandon: `git checkout master && git branch -D feature/m3-expressive`. Never mutate `master` directly.

---

## PHASE 10 VERIFICATION MATRIX

| Check | Pass condition |
|-------|----------------|
| Catalog | `gradlew app:dependencies` shows `material3:… -> 1.5.0-alpha23` and `ui-android:… -> 1.12.0-alpha03` |
| Symbols resolve | `compileDebugKotlin` green with all six Expressive symbols in use |
| No split versions | no `androidx.compose.*` group resolves to two different minor trains |
| Theme | `MaterialExpressiveTheme(` present in Theme.kt; `MaterialTheme(` absent from `BiapTheme`; `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` on `BiapTheme` |
| MainScreen | `titleLargeEmphasized`, `LoadingIndicator`, `LinearWavyProgressIndicator` each present; `LOW_POWER` fallbacks intact |
| DesignDetail | single `LoadingIndicator()`, zero `CircularProgressIndicator()` |
| Tests | `gradlew test` → 14 passing |
| Release | `assembleRelease` succeeds (with at most owner-approved `-dontwarn` additions) |
| Device | TASK-A6 checklist all green |
| Prime Directive #2 | Material 3 Expressive is now genuinely in use — SATISFIED |

*End of Option A manual. Versions are empirically verified; the code edits are anchored to the current `master`. Execute in order.*
