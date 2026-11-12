# Overload Workout Tracker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build "Overload", an offline Android app for planning routines, logging workouts set by set, and charting progressive overload, exactly as the spec describes.

**Architecture:** One Gradle module (`:app`), one activity, Jetpack Compose. Three packages with a one-way dependency: `domain` (pure Kotlin rules, no Android imports, JVM unit tests) ← `data` (Room database and repositories, instrumented tests against in-memory Room) ← `ui` (stateless `*Content` composables fed by thin ViewModels). Dependencies are wired by hand in an `AppContainer`. Loads, records and chart series are always computed by `domain` functions from saved workouts and never stored.

**Tech Stack:** Kotlin 2.2.10 (AGP 9.5.1 built-in Kotlin), Compose BOM 2026.09.00 with Material 3, Navigation Compose 2.10.2 with type-safe routes, Lifecycle 2.11.0, Room 2.8.5 with KSP 2.3.13 and the Room Gradle plugin, kotlinx-serialization-json 1.9.0, JUnit 4. Charts are drawn with Compose `Canvas`. No Hilt, no DataStore, no image or chart library.

**Spec:** `docs/superpowers/specs/2026-11-12-overload-workout-tracker-design.md` (read it first; section numbers below, such as "spec 5.7", refer to it). Approved mockups: `docs/superpowers/specs/mockups/*.png`.

## Global Constraints

- **No permissions.** `AndroidManifest.xml` has no `<uses-permission>` element. Never add INTERNET.
- **No Android backup.** `android:allowBackup="false"`; backup and data-extraction rules exclude everything for cloud backup and device transfer.
- **Ids and names.** `applicationId = "app.overload.tracker"`. Code namespace stays `com.example.fit`. The app name "Overload" exists only as `@string/app_name`.
- **`minSdk = 26`** (raised from the template's 24 so `java.time` works without desugaring). `compileSdk`/`targetSdk` stay 37.
- **Weights are integers in hectograms** (`Hg`, 1 hg = 0.1 kg) in all code and in the database: 42.5 kg is `425`. Loads are `Long` hectograms. Never store or compare weights as `Float`/`Double`.
- **Steps and limits.** Weight ±0.5 kg (5 hg), 0 to 999.5 kg. Reps ±1, 1 to 999. Cardio minutes ±1, 1 to 999. Body weight precision 0.1 kg.
- **Workout numbers are buttons only.** Weight, reps and minutes on Today, in the routine editor and in history change only through − / + controls. No text field for them anywhere.
- **App day** starts at 04:00 phone local time. Weeks run Monday to Sunday.
- **Only ticked sets count** in history, loads, charts and records.
- **Every write is immediate.** A tick or a number change is written to the database in the same user action. No "save" step, no debounce.
- **Derived, not stored.** Records, loads, weekly totals and chart points are never persisted.
- **Strings.** Every user-visible string lives in `app/src/main/res/values/strings.xml`. English only.
- **Theme.** Follows the system light/dark setting. `dynamicColor` is off.
- **Chart colours** (spec 5.10): session dots `#898781`; moving average and load `#2A78D6` light / `#3987E5` dark; cardio `#EB6834` light / `#D95926` dark; grid `#E1E0D9` light / `#2C2C2A` dark; baseline `#C3C2B7` light / `#383835` dark; axis labels `#898781`. One y-scale per chart.
- **Dependencies.** Only those listed in Task 1. Do not add others.
- **Non-goals stay out** (spec section 2): no notifications, timers, exercise notes, how-to text, unit settings, calendar view, typing of workout numbers, past-date workouts, second workout in a day.
- **Git.** Work on branch `overload-v1`. Commit at the end of every task. Every commit message ends with these two trailer lines:

  ```
  Co-Authored-By: Claude Fable 5.2 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_014nVWgzLAmc7sCUxiqwafKv
  ```

## Environment

These were verified on this machine before the plan was written: the dependency set in Task 1 builds, and a Room test runs on the emulator.

```bash
# Every shell that runs Gradle needs this (Git Bash syntax):
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
SDK="/c/Users/hplna/AppData/Local/Android/Sdk"

# JVM unit tests (fast, no device):
./gradlew :app:testDebugUnitTest --console=plain
./gradlew :app:testDebugUnitTest --tests "com.example.fit.domain.LoadTest" --console=plain

# Instrumented tests need a booted emulator. Start it once and leave it running:
"$SDK/emulator/emulator.exe" -avd Medium_Phone_API_36.0 -no-window -no-snapshot-save -no-boot-anim -no-audio &
"$SDK/platform-tools/adb.exe" wait-for-device
"$SDK/platform-tools/adb.exe" shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'

# All instrumented tests, or one class:
./gradlew :app:connectedDebugAndroidTest --console=plain
./gradlew :app:connectedDebugAndroidTest --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.fit.data.WorkoutRepositoryTest
```

"Run unit tests" below means the first form; "run instrumented tests" means the last form with the named class. A passing run ends with `BUILD SUCCESSFUL`.

## Review Focus

Conditions the spec implies, that a person using the app will hit, and that are easy to leave untested. Each has a test in the task named.

1. **Holding − or + fires many steps quickly.** Every step must land; none may be lost to a read-then-write race. → Tasks 6 and 7 (`adjustSet_isAtomic_underRapidSteps`).
2. **The app is killed or the phone restarts mid-workout.** Every tick and number already made must be there on reopening. → Task 7 (`ticks_surviveReopeningTheDatabase`).
3. **A new user has no history.** Every chart, record tile and list must show a plain empty state, not crash or divide by zero. → Tasks 2, 3, 12, 17 and 18 (tests named `emptyHistory_*`).
4. **A wrong file is picked for Import** (a photo, a truncated file, a backup from a newer app version). The app must refuse it and leave all existing data untouched. → Task 10 (`import_rejectsBadFiles_andKeepsData`).
5. **A routine is edited or deleted while today's workout from it is in progress.** Finishing must still work, keep the history, and not resurrect or corrupt the routine. → Task 8 (`finish_afterRoutineDeleted_*`, `finish_afterExerciseRemovedFromRoutine_*`).

## File Structure

```
app/src/main/java/com/example/fit/
  OverloadApp.kt            Application; owns the AppContainer
  AppContainer.kt           builds database, repositories, clock, TodayHolder
  MainActivity.kt           setContent { OverloadTheme { AppNav() } }

  domain/                   pure Kotlin; imports only kotlin.* and java.time.*
    Models.kt               enums and data classes shared by every layer
    AppDay.kt               app day (04:00 start), week start, AppClock
    SetStepping.kt          − / + steps and limits for one set
    Load.kt                 set, exercise and workout load; reps and minutes totals
    Format.kt               kg, axis, date formatting; body weight parsing; age
    CarryForward.kt         what a routine shows next time (spec 5.7)
    MovingAverage.kt        trailing mean over N sessions
    DefaultSets.kt          starting sets for a never-done exercise
    WeekCount.kt            "X of Y done"
    ExerciseSearch.kt       search and muscle-group filter
    Records.kt              sessions, personal records, record badges, counts
    ChartSeries.kt          exercise, routine and weekly series; axis ticks
    TodayState.kt           what the Today screen shows, as a pure function

  data/
    db/Entities.kt          every Room entity (schema version 1)
    db/Daos.kt              ExerciseDao, ProfileDao, RoutineDao, WorkoutDao
    db/AppDatabase.kt       RoomDatabase + type converters
    ExerciseRepository.kt   built-in and custom exercises
    ProfileRepository.kt    profile, body weight history, moving-average N
    StartingSets.kt         starting sets for an exercise newly added somewhere
    RoutineRepository.kt    routines, their starting sets, weekly schedule
    WorkoutRepository.kt    today's workout, finishing, day close, history
    Seeder.kt               built-in exercises from assets; starter routines
    StarterData.kt          the five starter routines and the starter schedule
    TodayHolder.kt          current app day as a StateFlow
    backup/BackupModels.kt  @Serializable backup file shape
    backup/BackupCodec.kt   encode / decode / validate (pure)
    backup/BackupRepository.kt  export and import against the database

  ui/
    ViewModels.kt           overloadViewModel { container -> ... }
    theme/                  Color.kt, Theme.kt, Type.kt
    nav/Routes.kt, nav/AppNav.kt
    components/             StepButton.kt, ExerciseImage.kt, Common.kt, SetRow.kt,
                            ChartLayout.kt, Charts.kt
    today/                  TodayViewModel.kt, TodayScreen.kt
    routines/               RoutinesViewModel.kt, RoutinesScreen.kt,
                            RoutineEditorViewModel.kt, RoutineEditorScreen.kt
    picker/                 ExercisePickerViewModel.kt, ExercisePickerScreen.kt
    progress/               ProgressViewModel.kt, ProgressScreen.kt
    exercise/               ExerciseDetailViewModel.kt, ExerciseDetailScreen.kt
    history/                HistoryViewModel.kt, HistoryScreen.kt,
                            WorkoutDetailViewModel.kt, WorkoutDetailScreen.kt
    profile/                ProfileViewModel.kt, ProfileScreen.kt, WelcomeScreen.kt

app/src/main/assets/exercises.json        the built-in exercise list
app/src/main/assets/exercises/*.webp      two pictures per built-in exercise
app/schemas/                              exported Room schema (committed)
tools/exercise-pack/                      Node script that builds the two assets above
```

Tests mirror this: `app/src/test/java/com/example/fit/<package>/` for JVM tests, `app/src/androidTest/java/com/example/fit/<package>/` for instrumented tests.

Every screen follows one pattern: `XScreen(...)` reads a ViewModel and passes plain state and callbacks to `XContent(state, ...)`. Only `XContent` is tested with Compose tests, so those tests need no database.

---

### Task 1: Project setup and domain foundation

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `.gitignore`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`, `app/src/main/res/values/strings.xml`
- Delete: `app/src/test/java/com/example/fit/ExampleUnitTest.kt`, `app/src/androidTest/java/com/example/fit/ExampleInstrumentedTest.kt`
- Create: `app/src/main/java/com/example/fit/domain/{Models,AppDay,SetStepping,Load,Format}.kt`
- Test: `app/src/test/java/com/example/fit/ManifestTest.kt`, `app/src/test/java/com/example/fit/domain/{AppDayTest,SetSteppingTest,LoadTest,FormatTest}.kt`

**Interfaces:**
- Consumes: nothing.
- Produces (used by every later task):

```kotlin
// domain/Models.kt
enum class ExerciseKind { WEIGHTS, BODY_WEIGHT, REPS_ONLY, CARDIO }
enum class MuscleGroup { CHEST, BACK, SHOULDERS, ARMS, LEGS, CORE, CARDIO }

data class Exercise(
    val id: String,
    val name: String,
    val group: MuscleGroup,
    val muscle: String?,          // e.g. "Triceps"; null for custom exercises
    val equipment: String?,       // "barbell", "dumbbell", "cable", "machine", "body only", "other"; null for custom
    val kind: ExerciseKind,
    val isCustom: Boolean = false,
    val isHidden: Boolean = false,
    val imageBase: String? = null, // asset path stem such as "exercises/bench-press"; null means muscle icon
)

/** weightHg is hectograms; for BODY_WEIGHT it is the extra weight. Unused fields stay 0. */
data class SetValues(val weightHg: Int = 0, val reps: Int = 0, val minutes: Int = 0)

data class RoutineExercise(val exercise: Exercise, val sets: List<SetValues>)
data class Routine(val id: Long, val name: String, val exercises: List<RoutineExercise>)

data class WorkoutSet(val position: Int, val values: SetValues, val done: Boolean)
data class WorkoutExercise(val exercise: Exercise, val sets: List<WorkoutSet>)
enum class WorkoutStatus { IN_PROGRESS, FINISHED }
data class Workout(
    val id: Long,
    val date: LocalDate,          // app day
    val routineId: Long?,         // null once the routine is deleted
    val routineName: String,      // name when the workout was created
    val status: WorkoutStatus,
    val bodyWeightHg: Int?,
    val exercises: List<WorkoutExercise>,
)

data class Profile(
    val name: String = "",
    val dateOfBirth: LocalDate? = null,
    val heightFeet: Int? = null,
    val heightInches: Int? = null,
    val welcomeDone: Boolean = false,
)
data class BodyWeightEntry(val date: LocalDate, val weightHg: Int)

// domain/AppDay.kt
const val DAY_START_HOUR = 4
fun interface AppClock { fun now(): LocalDateTime }
fun appDay(now: LocalDateTime): LocalDate
fun weekStart(date: LocalDate): LocalDate            // the Monday on or before date

// domain/SetStepping.kt
enum class SetField { WEIGHT, REPS, MINUTES }
const val WEIGHT_STEP_HG = 5
const val MAX_WEIGHT_HG = 9995
const val MAX_REPS = 999
const val MAX_MINUTES = 999
fun SetValues.stepped(field: SetField, steps: Int): SetValues

// domain/Load.kt
fun effectiveWeightHg(kind: ExerciseKind, weightHg: Int, bodyWeightHg: Int?): Int
fun setLoadHg(kind: ExerciseKind, values: SetValues, bodyWeightHg: Int?): Long
fun WorkoutExercise.doneSets(): List<WorkoutSet>
fun WorkoutExercise.loadHg(bodyWeightHg: Int?): Long   // ticked sets only
fun WorkoutExercise.totalReps(): Int                   // ticked sets only
fun WorkoutExercise.totalMinutes(): Int                // ticked sets only; 0 unless CARDIO
fun Workout.loadHg(): Long
fun Workout.cardioMinutes(): Int
fun Workout.doneSetCount(): Int

// domain/Format.kt  (all formatting uses Locale.US)
fun formatKg(hg: Long): String              // "1,010 kg", "42.5 kg", "0 kg"
fun formatStepWeight(hg: Int): String       // "40.0", "42.5": always one decimal, no unit
fun formatAxis(value: Double): String       // "600", "1,000", "20K", "17.2K"
fun parseKgToHg(text: String): Int?         // "72.3" -> 723; null unless 20.0..400.0 kg with at most one decimal
fun ageOn(dateOfBirth: LocalDate, today: LocalDate): Int
fun formatDayLong(date: LocalDate): String  // "Thursday, 12 November"
fun formatDayShort(date: LocalDate): String // "5 Nov"
```

- [ ] **Step 1: Replace the build configuration with the verified one**

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.5.1"
kotlin = "2.2.10"
ksp = "2.3.13"
coreKtx = "1.19.1"
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
lifecycle = "2.11.0"
activityCompose = "1.13.0"
composeBom = "2026.09.00"
room = "2.8.5"
navigation = "2.10.2"
serialization = "1.9.0"
coroutines = "1.10.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
```

Root `build.gradle.kts`: add `alias(libs.plugins.kotlin.serialization) apply false`, `alias(libs.plugins.ksp) apply false` and `alias(libs.plugins.room) apply false` beside the two existing aliases.

`app/build.gradle.kts`, changes only:
- `plugins { }` gains `alias(libs.plugins.kotlin.serialization)`, `alias(libs.plugins.ksp)`, `alias(libs.plugins.room)`.
- `applicationId = "app.overload.tracker"`, `minSdk = 26`.
- After the `android { }` block: `room { schemaDirectory("$projectDir/schemas") }`.
- `dependencies { }` gains:

```kotlin
implementation(libs.androidx.compose.material.icons.core)
implementation(libs.androidx.lifecycle.runtime.compose)
implementation(libs.androidx.lifecycle.viewmodel.compose)
implementation(libs.androidx.navigation.compose)
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)
implementation(libs.kotlinx.serialization.json)
testImplementation(libs.kotlinx.coroutines.test)
androidTestImplementation(libs.androidx.room.testing)
androidTestImplementation(libs.kotlinx.coroutines.test)
```

`.gitignore`: add `node_modules/` and `tools/exercise-pack/.cache/`.

- [ ] **Step 2: Switch off backup and rename the app**

- `AndroidManifest.xml`: `android:allowBackup="false"`. Leave the two `@xml/…` rule attributes in place.
- `backup_rules.xml`: inside `<full-backup-content>`, one `<exclude domain="D" path="."/>` for each `D` in `root`, `file`, `database`, `sharedpref`, `external`.
- `data_extraction_rules.xml`: a `<cloud-backup>` and a `<device-transfer>` element, each holding the same five excludes.
- `strings.xml`: `app_name` becomes `Overload`.
- Delete the two template example tests.

- [ ] **Step 3: Verify the project still builds**

Run: `./gradlew :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Write the failing tests**

```kotlin
// ManifestTest.kt  (unit tests run with the module directory `app/` as working directory)
class ManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    @Test fun requestsNoPermission() = assertFalse(manifest.contains("uses-permission"))
    @Test fun backupIsOff() = assertTrue(manifest.contains("android:allowBackup=\"false\""))
    @Test fun dataExtractionExcludesDatabaseForBothChannels() {
        val rules = File("src/main/res/xml/data_extraction_rules.xml").readText()
        assertEquals(2, Regex("<exclude domain=\"database\"").findAll(rules).count())
    }
}

// AppDayTest.kt
class AppDayTest {
    @Test fun dayStartsAtFourAm() {
        assertEquals(LocalDate.of(2026, 11, 12), appDay(LocalDateTime.of(2026, 11, 12, 4, 0)))
        assertEquals(LocalDate.of(2026, 11, 11), appDay(LocalDateTime.of(2026, 11, 12, 3, 59)))
    }
    @Test fun lateSessionStaysOnTheDayItStarted() =
        assertEquals(LocalDate.of(2026, 11, 12), appDay(LocalDateTime.of(2026, 11, 13, 0, 30)))
    @Test fun weekStartsOnMonday() {
        assertEquals(LocalDate.of(2026, 11, 9), weekStart(LocalDate.of(2026, 11, 12)))  // Thursday
        assertEquals(LocalDate.of(2026, 11, 9), weekStart(LocalDate.of(2026, 11, 9)))   // Monday
        assertEquals(LocalDate.of(2026, 11, 9), weekStart(LocalDate.of(2026, 11, 15)))  // Sunday
    }
}

// SetSteppingTest.kt
class SetSteppingTest {
    @Test fun weightMovesInHalfKilos() {
        assertEquals(SetValues(405, 10), SetValues(400, 10).stepped(SetField.WEIGHT, +1))
        assertEquals(SetValues(390, 10), SetValues(400, 10).stepped(SetField.WEIGHT, -2))
    }
    @Test fun valuesStayInsideLimits() {
        assertEquals(0, SetValues(weightHg = 0).stepped(SetField.WEIGHT, -1).weightHg)
        assertEquals(9995, SetValues(weightHg = 9995).stepped(SetField.WEIGHT, +1).weightHg)
        assertEquals(1, SetValues(reps = 1).stepped(SetField.REPS, -1).reps)
        assertEquals(999, SetValues(reps = 999).stepped(SetField.REPS, +1).reps)
        assertEquals(1, SetValues(minutes = 1).stepped(SetField.MINUTES, -1).minutes)
        assertEquals(999, SetValues(minutes = 999).stepped(SetField.MINUTES, +1).minutes)
    }
    @Test fun steppingOneFieldLeavesTheOthers() =
        assertEquals(SetValues(400, 11, 0), SetValues(400, 10, 0).stepped(SetField.REPS, +1))
}

// LoadTest.kt
class LoadTest {
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private fun ex(kind: ExerciseKind, vararg sets: WorkoutSet) = WorkoutExercise(bench.copy(kind = kind), sets.toList())
    private fun set(pos: Int, hg: Int, reps: Int, done: Boolean = true, minutes: Int = 0) =
        WorkoutSet(pos, SetValues(hg, reps, minutes), done)

    @Test fun exerciseLoadAddsTickedSets() {                 // spec example: 40x10 + 42.5x8 + 45x6 = 1010 kg
        val e = ex(ExerciseKind.WEIGHTS, set(0, 400, 10), set(1, 425, 8), set(2, 450, 6))
        assertEquals(10100L, e.loadHg(null))
    }
    @Test fun untickedSetsAddNothing() {
        val e = ex(ExerciseKind.WEIGHTS, set(0, 400, 10), set(1, 425, 8), set(2, 450, 6, done = false))
        assertEquals(7400L, e.loadHg(null))
        assertEquals(18, e.totalReps())
    }
    @Test fun bodyWeightExerciseUsesBodyWeightPlusExtra() {  // spec example: 10 pull-ups at 70 kg = 700 kg
        assertEquals(7000L, ex(ExerciseKind.BODY_WEIGHT, set(0, 0, 10)).loadHg(700))
        assertEquals(7500L, ex(ExerciseKind.BODY_WEIGHT, set(0, 50, 10)).loadHg(700))
        assertEquals(750, effectiveWeightHg(ExerciseKind.BODY_WEIGHT, 50, 700))
    }
    @Test fun repsOnlyAndCardioHaveNoLoad() {
        assertEquals(0L, ex(ExerciseKind.REPS_ONLY, set(0, 0, 20)).loadHg(700))
        val cardio = ex(ExerciseKind.CARDIO, set(0, 0, 0, minutes = 25))
        assertEquals(0L, cardio.loadHg(700))
        assertEquals(25, cardio.totalMinutes())
    }
    @Test fun workoutTotals() {
        val w = Workout(1, LocalDate.of(2026, 11, 9), 1, "Chest", WorkoutStatus.FINISHED, 700, listOf(
            ex(ExerciseKind.WEIGHTS, set(0, 400, 10), set(1, 425, 8, done = false)),
            ex(ExerciseKind.CARDIO, set(0, 0, 0, minutes = 25)),
        ))
        assertEquals(4000L, w.loadHg()); assertEquals(25, w.cardioMinutes()); assertEquals(2, w.doneSetCount())
    }
}

// FormatTest.kt
class FormatTest {
    @Test fun kilograms() {
        assertEquals("1,010 kg", formatKg(10100)); assertEquals("42.5 kg", formatKg(425))
        assertEquals("0 kg", formatKg(0)); assertEquals("17,200 kg", formatKg(172000))
        assertEquals("40.0", formatStepWeight(400)); assertEquals("42.5", formatStepWeight(425))
    }
    @Test fun axisLabels() {
        assertEquals("600", formatAxis(600.0)); assertEquals("1,000", formatAxis(1000.0))
        assertEquals("20K", formatAxis(20000.0)); assertEquals("17.2K", formatAxis(17200.0))
    }
    @Test fun bodyWeightParsing() {
        assertEquals(723, parseKgToHg("72.3")); assertEquals(720, parseKgToHg("72")); assertEquals(720, parseKgToHg(" 72.0 "))
        assertNull(parseKgToHg("72.35")); assertNull(parseKgToHg("abc")); assertNull(parseKgToHg(""))
        assertNull(parseKgToHg("19.9")); assertNull(parseKgToHg("400.1"))
    }
    @Test fun age() {
        assertEquals(30, ageOn(LocalDate.of(1996, 3, 14), LocalDate.of(2026, 11, 12)))
        assertEquals(29, ageOn(LocalDate.of(1996, 12, 1), LocalDate.of(2026, 11, 12)))
    }
    @Test fun dates() {
        assertEquals("Thursday, 12 November", formatDayLong(LocalDate.of(2026, 11, 12)))
        assertEquals("5 Nov", formatDayShort(LocalDate.of(2026, 11, 5)))
    }
}
```

- [ ] **Step 5: Run unit tests and confirm they fail**

Expected: compilation fails with unresolved references to `appDay`, `SetValues`, `formatKg` and the rest. `ManifestTest` alone would pass.

- [ ] **Step 6: Implement the five domain files with the signatures in Interfaces**

- [ ] **Step 7: Run unit tests and confirm they pass**

Expected: `BUILD SUCCESSFUL`, all tests in the five classes green.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "Set up dependencies, switch off backup, add domain foundation"
```

---

### Task 2: Domain rules for carrying numbers forward, averages and search

**Files:**
- Create: `app/src/main/java/com/example/fit/domain/{CarryForward,MovingAverage,DefaultSets,WeekCount,ExerciseSearch}.kt`
- Test: `app/src/test/java/com/example/fit/domain/{CarryForwardTest,MovingAverageTest,DefaultSetsTest,WeekCountTest,ExerciseSearchTest}.kt`

**Interfaces:**
- Consumes: `SetValues`, `WorkoutSet`, `Exercise`, `ExerciseKind`, `MuscleGroup`, `weekStart` from Task 1.
- Produces:

```kotlin
/** Spec 5.7. workoutSets are in position order and include unticked sets. Null means "leave the routine alone". */
fun nextStartingSets(routineSets: List<SetValues>, workoutSets: List<WorkoutSet>): List<SetValues>?

/** Trailing mean: element i is the mean of values[max(0, i-n+1)..i]. Requires n >= 1. */
fun movingAverage(values: List<Double>, n: Int): List<Double>
const val DEFAULT_AVERAGE_N = 10
const val MIN_AVERAGE_N = 2
const val MAX_AVERAGE_N = 20

/** Spec 5.7 last bullet and section 10. */
fun defaultStartingSets(kind: ExerciseKind, equipment: String?): List<SetValues>

data class WeekCount(val done: Int, val planned: Int)
fun weekCount(savedDates: Collection<LocalDate>, scheduledDays: Set<DayOfWeek>, today: LocalDate): WeekCount

/** Hidden exercises never match. Every whitespace-separated term must appear in the name, ignoring case. Sorted by name. */
fun filterExercises(all: List<Exercise>, query: String, group: MuscleGroup?): List<Exercise>
```

- [ ] **Step 1: Write the failing tests**

```kotlin
// CarryForwardTest.kt
class CarryForwardTest {
    private fun s(hg: Int, reps: Int) = SetValues(hg, reps)
    private fun w(pos: Int, hg: Int, reps: Int, done: Boolean) = WorkoutSet(pos, s(hg, reps), done)
    private val routine = listOf(s(400, 10), s(425, 8), s(450, 6))

    @Test fun tickedSetsReplaceTheRoutineValues() = assertEquals(
        listOf(s(425, 10), s(425, 9), s(455, 6)),
        nextStartingSets(routine, listOf(w(0, 425, 10, true), w(1, 425, 9, true), w(2, 455, 6, true))))
    @Test fun untickedSetKeepsItsOldRoutineValues() = assertEquals(   // owner: "still 3 sets"
        listOf(s(425, 10), s(450, 8), s(450, 6)),
        nextStartingSets(routine, listOf(w(0, 425, 10, true), w(1, 450, 8, true), w(2, 999, 1, false))))
    @Test fun addedSetNeverTickedIsDropped() = assertEquals(
        routine,
        nextStartingSets(routine, listOf(w(0, 400, 10, true), w(1, 425, 8, true), w(2, 450, 6, true), w(3, 450, 6, false))))
    @Test fun addedSetThatWasTickedIsKept() = assertEquals(
        routine + s(460, 4),
        nextStartingSets(routine, listOf(w(0, 400, 10, true), w(1, 425, 8, true), w(2, 450, 6, true), w(3, 460, 4, true))))
    @Test fun removedSetShortensTheRoutine() = assertEquals(
        listOf(s(400, 10), s(425, 9)),
        nextStartingSets(routine, listOf(w(0, 500, 5, false), w(1, 425, 9, true))))
    @Test fun nothingTickedLeavesTheRoutineAlone() =
        assertNull(nextStartingSets(routine, listOf(w(0, 405, 10, false), w(1, 425, 8, false))))
    @Test fun cardioMinutesCarryWhenTicked() = assertEquals(
        listOf(SetValues(minutes = 25)),
        nextStartingSets(listOf(SetValues(minutes = 10)), listOf(WorkoutSet(0, SetValues(minutes = 25), true))))
}

// MovingAverageTest.kt
class MovingAverageTest {
    @Test fun usesFewerValuesWhileHistoryIsShort() =
        assertEquals(listOf(720.0, 740.0, 740.0, 755.0), movingAverage(listOf(720.0, 760.0, 740.0, 800.0), 10))
    @Test fun windowSlides() =
        assertEquals(listOf(1.0, 1.5, 2.5, 3.5, 4.5), movingAverage(listOf(1.0, 2.0, 3.0, 4.0, 5.0), 2))
    @Test fun mockupSeriesEndsAt893() {   // the series drawn in mockup 03, screen 1
        val loads = listOf(720, 760, 740, 800, 780, 840, 820, 880, 860, 900, 940, 920, 1010, 980).map { it.toDouble() }
        assertEquals(893.0, movingAverage(loads, 10).last(), 1e-9)
    }
    @Test fun emptyHistory_givesEmptyAverage() = assertEquals(emptyList<Double>(), movingAverage(emptyList(), 10))
}

// DefaultSetsTest.kt
class DefaultSetsTest {
    private fun three(hg: Int) = List(3) { SetValues(hg, 10) }
    @Test fun weightsStartByEquipment() {
        assertEquals(three(200), defaultStartingSets(ExerciseKind.WEIGHTS, "barbell"))
        assertEquals(three(50), defaultStartingSets(ExerciseKind.WEIGHTS, "dumbbell"))
        assertEquals(three(100), defaultStartingSets(ExerciseKind.WEIGHTS, "cable"))
        assertEquals(three(100), defaultStartingSets(ExerciseKind.WEIGHTS, "machine"))
        assertEquals(three(0), defaultStartingSets(ExerciseKind.WEIGHTS, "other"))
        assertEquals(three(0), defaultStartingSets(ExerciseKind.WEIGHTS, null))
    }
    @Test fun otherKinds() {
        assertEquals(three(0), defaultStartingSets(ExerciseKind.BODY_WEIGHT, "body only"))
        assertEquals(three(0), defaultStartingSets(ExerciseKind.REPS_ONLY, "body only"))
        assertEquals(listOf(SetValues(minutes = 10)), defaultStartingSets(ExerciseKind.CARDIO, "machine"))
    }
}

// WeekCountTest.kt
class WeekCountTest {
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    @Test fun countsSavedDaysThisWeekAgainstScheduledDays() = assertEquals(
        WeekCount(done = 3, planned = 6),
        weekCount(listOf(d(2), d(9), d(10), d(11)), EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), d(12)))
    @Test fun emptyHistory_andEmptySchedule() =
        assertEquals(WeekCount(0, 0), weekCount(emptyList(), emptySet(), d(12)))
}

// ExerciseSearchTest.kt
class ExerciseSearchTest {
    private fun e(name: String, group: MuscleGroup, hidden: Boolean = false) =
        Exercise(name.lowercase().replace(' ', '-'), name, group, null, null, ExerciseKind.WEIGHTS, isHidden = hidden)
    private val all = listOf(
        e("Leg Press", MuscleGroup.LEGS), e("Bench Press", MuscleGroup.CHEST),
        e("Incline Dumbbell Press", MuscleGroup.CHEST), e("Dumbbell Shoulder Press", MuscleGroup.SHOULDERS),
        e("Barbell Curl", MuscleGroup.ARMS), e("Old Press", MuscleGroup.CHEST, hidden = true))

    @Test fun matchesAnyPartOfTheNameIgnoringCase_sortedByName() = assertEquals(
        listOf("Bench Press", "Dumbbell Shoulder Press", "Incline Dumbbell Press", "Leg Press"),
        filterExercises(all, "PRESS", null).map { it.name })
    @Test fun everyTermMustMatch() = assertEquals(
        listOf("Dumbbell Shoulder Press", "Incline Dumbbell Press"), filterExercises(all, "dumb pre", null).map { it.name })
    @Test fun groupFilterCombinesWithQuery() =
        assertEquals(listOf("Bench Press", "Incline Dumbbell Press"), filterExercises(all, "press", MuscleGroup.CHEST).map { it.name })
    @Test fun blankQueryListsEverythingVisible() = assertEquals(5, filterExercises(all, "  ", null).size)
}
```

- [ ] **Step 2: Run unit tests and confirm they fail** (unresolved references)

- [ ] **Step 3: Implement the five files with the signatures in Interfaces**

- [ ] **Step 4: Run unit tests and confirm they pass**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add carry-forward, moving average, default sets, week count and search rules"
```

---

### Task 3: Domain rules for records and chart series

**Files:**
- Create: `app/src/main/java/com/example/fit/domain/{Records,ChartSeries}.kt`
- Test: `app/src/test/java/com/example/fit/domain/{RecordsTest,ChartSeriesTest}.kt`

**Interfaces:**
- Consumes: Task 1 models and load functions; `movingAverage` from Task 2.
- Produces:

```kotlin
// Records.kt
/** One exercise in one saved workout. sets holds ticked sets only, in order. */
data class ExerciseSession(
    val workoutId: Long, val date: LocalDate, val routineId: Long?,
    val kind: ExerciseKind, val bodyWeightHg: Int?, val sets: List<SetValues>,
)
/** Ascending by date. Skips workouts in which the exercise has no ticked set. */
fun List<Workout>.sessionsOf(exerciseId: String): List<ExerciseSession>

data class Dated<T>(val value: T, val date: LocalDate)
data class BestSet(val loadHg: Long, val weightHg: Int, val reps: Int, val date: LocalDate)   // weightHg is the effective weight
data class ExerciseRecords(
    val maxWeightHg: Dated<Int>? = null,        // WEIGHTS, BODY_WEIGHT
    val bestSet: BestSet? = null,               // WEIGHTS, BODY_WEIGHT
    val bestSessionHg: Dated<Long>? = null,     // WEIGHTS, BODY_WEIGHT
    val mostRepsSet: Dated<Int>? = null,        // REPS_ONLY
    val mostRepsSession: Dated<Int>? = null,    // REPS_ONLY
    val longestMinutes: Dated<Int>? = null,     // CARDIO
)
/** Each date is the first date the record value was reached. */
fun exerciseRecords(sessions: List<ExerciseSession>): ExerciseRecords

enum class RecordType { MAX_WEIGHT, BEST_SET, BEST_SESSION, MOST_REPS_SET, MOST_REPS_SESSION }
data class RecordBroken(val exerciseId: String, val type: RecordType)

/** Record types this session beats, compared with the sessions before it. A type with no earlier value is never "broken". CARDIO breaks nothing. */
fun recordsBroken(before: List<ExerciseSession>, session: ExerciseSession): Set<RecordType>
/** Positions of ticked sets that earn the "New record" badge: walking sets in position order, a set earns it when it beats
 *  the best max weight or best set (most reps in one set for REPS_ONLY) seen so far, counting `before` and earlier sets. */
fun recordSetPositions(before: List<ExerciseSession>, kind: ExerciseKind, bodyWeightHg: Int?, sets: List<WorkoutSet>): Set<Int>
/** Records `workout` breaks against saved workouts dated before it. */
fun workoutRecords(saved: List<Workout>, workout: Workout): List<RecordBroken>
/** workoutId -> number of records broken, for every saved workout. */
fun recordCounts(saved: List<Workout>): Map<Long, Int>

// ChartSeries.kt
enum class Measure { LOAD_KG, REPS, MINUTES }
data class ChartPoint(val date: LocalDate, val value: Double)
data class SessionSeries(val measure: Measure, val points: List<ChartPoint>, val average: List<Double>)

/** routineId null means all routines. LOAD_KG values are kilograms (hg / 10.0). */
fun exerciseSeries(kind: ExerciseKind, sessions: List<ExerciseSession>, routineId: Long?, n: Int): SessionSeries
/** workouts are the saved workouts of one routine, ascending. LOAD_KG unless every workout has zero load:
 *  then MINUTES if any has cardio minutes, otherwise REPS (spec 5.10). */
fun routineSeries(workouts: List<Workout>, n: Int): SessionSeries

data class WeekTotal(val weekStart: LocalDate, val loadHg: Long, val cardioMinutes: Int, val workouts: Int)
/** One entry per week from the first saved workout's week through today's week, empty weeks included. */
fun weeklyTotals(saved: List<Workout>, today: LocalDate): List<WeekTotal>

/** Axis ticks covering [min, max]. If max == min, first widen the range by 1 on each side.
 *  step = the smallest of 1, 2, 2.5, 5, 10 times a power of ten that is >= (max - min) / 2;
 *  ticks = every multiple of step from floor(min / step) * step to ceil(max / step) * step. */
fun niceTicks(min: Double, max: Double): List<Double>
```

- [ ] **Step 1: Write the failing tests**

```kotlin
// RecordsTest.kt
class RecordsTest {
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private fun s(hg: Int, reps: Int) = SetValues(hg, reps)
    private fun session(id: Long, day: Int, vararg sets: SetValues, kind: ExerciseKind = ExerciseKind.WEIGHTS,
                        routineId: Long? = 1, bw: Int? = null) = ExerciseSession(id, d(day), routineId, kind, bw, sets.toList())
    private val nov2 = session(1, 2, s(400, 10), s(425, 8))                // max 425, best set 4000, session 7400
    private val nov5 = session(2, 5, s(400, 10), s(425, 8), s(450, 6))     // max 450, best set 4000, session 10100
    private val nov9 = session(3, 9, s(400, 10), s(425, 8), s(450, 5))     // session 9650

    @Test fun recordsCarryTheFirstDateTheyWereReached() {
        val r = exerciseRecords(listOf(nov2, nov5, nov9))
        assertEquals(Dated(450, d(5)), r.maxWeightHg)
        assertEquals(BestSet(4000, 400, 10, d(2)), r.bestSet)
        assertEquals(Dated(10100L, d(5)), r.bestSessionHg)
        assertNull(r.mostRepsSet); assertNull(r.longestMinutes)
    }
    @Test fun emptyHistory_hasNoRecords() = assertEquals(ExerciseRecords(), exerciseRecords(emptyList()))
    @Test fun aTieIsNotANewRecord() =
        assertEquals(setOf(RecordType.MAX_WEIGHT, RecordType.BEST_SESSION), recordsBroken(listOf(nov2), nov5))
    @Test fun firstSessionBreaksNothing() = assertEquals(emptySet<RecordType>(), recordsBroken(emptyList(), nov2))
    @Test fun bodyWeightExerciseUsesEffectiveWeight() {
        val r = exerciseRecords(listOf(session(1, 2, s(0, 10), kind = ExerciseKind.BODY_WEIGHT, bw = 700)))
        assertEquals(700, r.maxWeightHg?.value); assertEquals(7000L, r.bestSet?.loadHg)
    }
    @Test fun repsOnlyAndCardioRecords() {
        val reps = exerciseRecords(listOf(
            session(1, 2, s(0, 20), s(0, 15), kind = ExerciseKind.REPS_ONLY),
            session(2, 5, s(0, 25), s(0, 5), kind = ExerciseKind.REPS_ONLY)))
        assertEquals(Dated(25, d(5)), reps.mostRepsSet); assertEquals(Dated(35, d(2)), reps.mostRepsSession)
        val cardio = exerciseRecords(listOf(
            session(1, 2, SetValues(minutes = 20), kind = ExerciseKind.CARDIO),
            session(2, 5, SetValues(minutes = 35), kind = ExerciseKind.CARDIO)))
        assertEquals(Dated(35, d(5)), cardio.longestMinutes)
        assertEquals(emptySet<RecordType>(),
            recordsBroken(listOf(session(1, 2, SetValues(minutes = 20), kind = ExerciseKind.CARDIO)),
                session(2, 5, SetValues(minutes = 35), kind = ExerciseKind.CARDIO)))
    }
    @Test fun badgeGoesToEachSetThatBeatsTheBestSoFar() {
        fun w(pos: Int, hg: Int, reps: Int, done: Boolean = true) = WorkoutSet(pos, s(hg, reps), done)
        assertEquals(setOf(2), recordSetPositions(listOf(nov2), ExerciseKind.WEIGHTS, null,
            listOf(w(0, 400, 10), w(1, 425, 8), w(2, 450, 6))))
        assertEquals(setOf(0, 2), recordSetPositions(listOf(nov2), ExerciseKind.WEIGHTS, null,
            listOf(w(0, 430, 10), w(1, 425, 8), w(2, 450, 6))))
        assertEquals(emptySet<Int>(), recordSetPositions(listOf(nov2), ExerciseKind.WEIGHTS, null,
            listOf(w(0, 400, 10), w(1, 425, 8), w(2, 450, 6, done = false))))
        assertEquals(emptySet<Int>(), recordSetPositions(emptyList(), ExerciseKind.WEIGHTS, null, listOf(w(0, 500, 10))))
    }
    @Test fun recordCountsPerWorkout() {
        val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
        fun workout(id: Long, day: Int, vararg sets: SetValues) = Workout(id, d(day), 1, "Chest", WorkoutStatus.FINISHED, null,
            listOf(WorkoutExercise(bench, sets.mapIndexed { i, v -> WorkoutSet(i, v, true) })))
        val saved = listOf(workout(1, 2, s(400, 10), s(425, 8)), workout(2, 5, s(400, 10), s(425, 8), s(450, 6)),
            workout(3, 9, s(400, 10), s(425, 8), s(450, 5)))
        assertEquals(mapOf(1L to 0, 2L to 2, 3L to 0), recordCounts(saved))
        assertEquals(listOf(RecordBroken("bench-press", RecordType.MAX_WEIGHT), RecordBroken("bench-press", RecordType.BEST_SESSION)),
            workoutRecords(saved, saved[1]).sortedBy { it.type })
        assertEquals(listOf(nov2, nov5, nov9), saved.sessionsOf("bench-press"))
    }
}

// ChartSeriesTest.kt
class ChartSeriesTest {
    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)
    private fun s(hg: Int, reps: Int) = SetValues(hg, reps)
    private fun session(day: Int, routineId: Long, vararg sets: SetValues, kind: ExerciseKind = ExerciseKind.WEIGHTS) =
        ExerciseSession(day.toLong(), d(11, day), routineId, kind, null, sets.toList())
    private val sessions = listOf(
        session(2, 1, s(400, 10), s(425, 8)),                 // 740 kg
        session(5, 2, s(400, 10), s(425, 8), s(450, 6)),      // 1010 kg
        session(9, 1, s(400, 10), s(425, 8), s(450, 5)))      // 965 kg

    @Test fun exerciseSeriesInKilogramsWithAverage() {
        val series = exerciseSeries(ExerciseKind.WEIGHTS, sessions, routineId = null, n = 2)
        assertEquals(Measure.LOAD_KG, series.measure)
        assertEquals(listOf(740.0, 1010.0, 965.0), series.points.map { it.value })
        assertEquals(listOf(d(11, 2), d(11, 5), d(11, 9)), series.points.map { it.date })
        assertEquals(listOf(740.0, 875.0, 987.5), series.average)
    }
    @Test fun routineFilterChangesTheAverageToo() {
        val series = exerciseSeries(ExerciseKind.WEIGHTS, sessions, routineId = 1, n = 2)
        assertEquals(listOf(740.0, 965.0), series.points.map { it.value }); assertEquals(listOf(740.0, 852.5), series.average)
    }
    @Test fun repsOnlyAndCardioUseTheirOwnMeasure() {
        val reps = exerciseSeries(ExerciseKind.REPS_ONLY, listOf(session(2, 1, s(0, 20), s(0, 15), kind = ExerciseKind.REPS_ONLY)), null, 10)
        assertEquals(Measure.REPS, reps.measure); assertEquals(listOf(35.0), reps.points.map { it.value })
        val cardio = exerciseSeries(ExerciseKind.CARDIO, listOf(session(2, 1, SetValues(minutes = 25), kind = ExerciseKind.CARDIO)), null, 10)
        assertEquals(Measure.MINUTES, cardio.measure); assertEquals(listOf(25.0), cardio.points.map { it.value })
    }
    @Test fun emptyHistory_givesEmptySeries() {
        assertEquals(SessionSeries(Measure.LOAD_KG, emptyList(), emptyList()), exerciseSeries(ExerciseKind.WEIGHTS, emptyList(), null, 10))
        assertEquals(emptyList<ChartPoint>(), routineSeries(emptyList(), 10).points)
        assertEquals(emptyList<WeekTotal>(), weeklyTotals(emptyList(), d(11, 12)))
    }

    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private val run = Exercise("treadmill", "Treadmill", MuscleGroup.CARDIO, null, "machine", ExerciseKind.CARDIO)
    private val crunch = Exercise("crunches", "Crunches", MuscleGroup.CORE, "Abs", "body only", ExerciseKind.REPS_ONLY)
    private fun workout(id: Long, date: LocalDate, vararg ex: WorkoutExercise) =
        Workout(id, date, 1, "R", WorkoutStatus.FINISHED, null, ex.toList())
    private fun lift(hg: Int, reps: Int) = WorkoutExercise(bench, listOf(WorkoutSet(0, s(hg, reps), true)))
    private fun cardio(min: Int) = WorkoutExercise(run, listOf(WorkoutSet(0, SetValues(minutes = min), true)))
    private fun crunches(reps: Int) = WorkoutExercise(crunch, listOf(WorkoutSet(0, s(0, reps), true)))

    @Test fun routineSeriesPicksItsMeasure() {
        assertEquals(Measure.LOAD_KG, routineSeries(listOf(workout(1, d(11, 2), lift(400, 10), cardio(10))), 10).measure)
        val cardioOnly = routineSeries(listOf(workout(1, d(11, 2), cardio(20)), workout(2, d(11, 5), cardio(35))), 10)
        assertEquals(Measure.MINUTES, cardioOnly.measure); assertEquals(listOf(20.0, 35.0), cardioOnly.points.map { it.value })
        assertEquals(Measure.REPS, routineSeries(listOf(workout(1, d(11, 2), crunches(40))), 10).measure)
    }
    @Test fun weeklyTotalsIncludeEmptyWeeksUpToToday() = assertEquals(
        listOf(WeekTotal(d(10, 19), 4000, 0, 1), WeekTotal(d(10, 26), 0, 0, 0),
               WeekTotal(d(11, 2), 14100, 0, 2), WeekTotal(d(11, 9), 5000, 20, 1)),
        weeklyTotals(listOf(
            workout(1, d(10, 19), lift(400, 10)), workout(2, d(11, 2), lift(400, 10)),
            workout(3, d(11, 5), lift(1010, 10)), workout(4, d(11, 12), lift(500, 10), cardio(20))), today = d(11, 12)))
    @Test fun axisTicks() {
        assertEquals(listOf(0.0, 10000.0, 20000.0), niceTicks(0.0, 17200.0))
        assertEquals(listOf(4000.0, 4500.0, 5000.0), niceTicks(4100.0, 4820.0))
        assertEquals(listOf(600.0, 800.0, 1000.0, 1200.0), niceTicks(720.0, 1010.0))
        assertEquals(listOf(499.0, 500.0, 501.0), niceTicks(500.0, 500.0))
    }
}
```

- [ ] **Step 2: Run unit tests and confirm they fail** (unresolved references)

- [ ] **Step 3: Implement `Records.kt` and `ChartSeries.kt` with the signatures in Interfaces**

`recordCounts` makes one pass over the workouts in date order, keeping the sessions seen so far per exercise.

- [ ] **Step 4: Run unit tests and confirm they pass**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add personal record and chart series rules"
```

---

### Task 4: Built-in exercise list and pictures

This task needs network access once, to download photos. Its outputs are committed, so later builds and the app itself never need the network.

**Files:**
- Create: `tools/exercise-pack/package.json`, `tools/exercise-pack/build-pack.mjs`, `tools/exercise-pack/selection.json`, `tools/exercise-pack/README.md`
- Create (generated by the script, committed): `app/src/main/assets/exercises.json`, `app/src/main/assets/exercises/<id>_0.webp`, `app/src/main/assets/exercises/<id>_1.webp`
- Create: `app/src/main/java/com/example/fit/data/BuiltInExercise.kt`
- Test: `app/src/test/java/com/example/fit/data/ExercisePackTest.kt`

**Interfaces:**
- Consumes: `MuscleGroup`, `ExerciseKind` from Task 1.
- Produces:
  - `assets/exercises.json`: a JSON array, sorted by name, of objects with exactly the fields of `BuiltInExercise`.
  - `assets/exercises/<id>_0.webp` (start position) and `<id>_1.webp` (end position) for every id.
  - `@Serializable data class BuiltInExercise(val id: String, val name: String, val group: MuscleGroup, val muscle: String, val equipment: String, val kind: ExerciseKind)`

**The selection (spec section 9).** `selection.json` is an array of `{ "id", "source", "name", "group", "muscle", "equipment", "kind" }`. `source` is the exercise id in `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json`; `equipment` is copied from that dataset; `muscle` is the dataset's first primary muscle in title case ("Middle Back", "Triceps"); `id` is a short lowercase-hyphen slug; `name` is a short familiar name.

Choose 140 to 160 exercises by the rules in spec section 9. These 25 must be present with exactly these ids, because starter routines and tests use them:

| id | source | name | kind |
|---|---|---|---|
| `squat` | `Barbell_Squat` | Squat | WEIGHTS |
| `bench-press` | `Barbell_Bench_Press_-_Medium_Grip` | Bench Press | WEIGHTS |
| `bent-over-row` | `Bent_Over_Barbell_Row` | Bent Over Row | WEIGHTS |
| `dumbbell-shoulder-press` | `Dumbbell_Shoulder_Press` | Dumbbell Shoulder Press | WEIGHTS |
| `lat-pulldown` | `Wide-Grip_Lat_Pulldown` | Lat Pulldown | WEIGHTS |
| `dumbbell-bicep-curl` | `Dumbbell_Bicep_Curl` | Dumbbell Bicep Curl | WEIGHTS |
| `triceps-pushdown` | `Triceps_Pushdown` | Triceps Pushdown | WEIGHTS |
| `incline-dumbbell-press` | `Incline_Dumbbell_Press` | Incline Dumbbell Press | WEIGHTS |
| `dumbbell-flyes` | `Dumbbell_Flyes` | Dumbbell Flyes | WEIGHTS |
| `overhead-triceps-extension` | `Cable_Rope_Overhead_Triceps_Extension` | Overhead Triceps Extension | WEIGHTS |
| `seated-cable-row` | `Seated_Cable_Rows` | Seated Cable Row | WEIGHTS |
| `barbell-curl` | `Barbell_Curl` | Barbell Curl | WEIGHTS |
| `hammer-curl` | `Hammer_Curls` | Hammer Curl | WEIGHTS |
| `leg-press` | `Leg_Press` | Leg Press | WEIGHTS |
| `leg-extension` | `Leg_Extensions` | Leg Extension | WEIGHTS |
| `lying-leg-curl` | `Lying_Leg_Curls` | Lying Leg Curl | WEIGHTS |
| `side-lateral-raise` | `Side_Lateral_Raise` | Side Lateral Raise | WEIGHTS |
| `pull-up` | `Pullups` | Pull-up | BODY_WEIGHT |
| `chin-up` | `Chin-Up` | Chin-up | BODY_WEIGHT |
| `push-up` | `Pushups` | Push-up | BODY_WEIGHT |
| `dips` | `Dips_-_Triceps_Version` | Dips | BODY_WEIGHT |
| `chest-dips` | `Dips_-_Chest_Version` | Chest Dips | BODY_WEIGHT |
| `crunches` | `Crunches` | Crunches | REPS_ONLY |
| `treadmill` | `Running_Treadmill` | Treadmill | CARDIO |
| `stationary-bike` | `Bicycling_Stationary` | Stationary Bike | CARDIO |

Group mapping from the dataset's first primary muscle: chest → CHEST; lats, middle back, lower back, traps → BACK; shoulders → SHOULDERS; biceps, triceps, forearms → ARMS; quadriceps, hamstrings, glutes, calves, adductors, abductors → LEGS; abdominals → CORE; dataset `category` "cardio" → CARDIO (with `muscle` "Cardio").

Kind rules: CARDIO for the CARDIO group; BODY_WEIGHT only for pull-up, chin-up, dip and push-up variants; REPS_ONLY for every other exercise whose dataset equipment is "body only"; WEIGHTS otherwise. Leave out the plank and other timed holds. Three dataset entries have no photos; do not pick them.

- [ ] **Step 1: Write the failing test**

```kotlin
class ExercisePackTest {
    private val list: List<BuiltInExercise> =
        Json.decodeFromString(File("src/main/assets/exercises.json").readText())
    private val dir = File("src/main/assets/exercises")
    private fun count(group: MuscleGroup) = list.count { it.group == group }

    @Test fun hasAbout150Exercises() = assertTrue("${list.size}", list.size in 140..160)
    @Test fun idsAndNamesAreUniqueAndWellFormed() {
        assertEquals(list.size, list.map { it.id }.toSet().size)
        assertEquals(list.size, list.map { it.name.lowercase() }.toSet().size)
        assertTrue(list.all { Regex("[a-z0-9]+(-[a-z0-9]+)*").matches(it.id) })
        assertEquals(list.map { it.name }.sortedBy { it.lowercase() }, list.map { it.name })
    }
    @Test fun everyExerciseHasTwoSmallPictures_andNothingElseIsBundled() {
        list.forEach { e -> for (i in 0..1) {
            val f = File(dir, "${e.id}_$i.webp")
            assertTrue("missing ${f.name}", f.isFile); assertTrue("${f.name} too big", f.length() < 40_000)
        } }
        assertEquals(list.size * 2, dir.list()!!.size)
        assertTrue(dir.listFiles()!!.sumOf { it.length() } < 10_000_000)
    }
    @Test fun everyMuscleGroupIsCovered() {
        for (g in MuscleGroup.entries - MuscleGroup.CARDIO) assertTrue("$g has ${count(g)}", count(g) >= 12)
        assertTrue(count(MuscleGroup.CARDIO) >= 5)
    }
    @Test fun kindsFollowTheSpec() {
        assertTrue(list.all { (it.group == MuscleGroup.CARDIO) == (it.kind == ExerciseKind.CARDIO) })
        val wholeBody = Regex("pull-?up|chin-?up|dip|push-?up", RegexOption.IGNORE_CASE)
        assertTrue(list.filter { it.kind == ExerciseKind.BODY_WEIGHT }.all { wholeBody.containsMatchIn(it.name) })
        assertTrue(list.filter { it.kind == ExerciseKind.REPS_ONLY }.all { it.equipment == "body only" })
        assertTrue(list.count { it.kind == ExerciseKind.REPS_ONLY } >= 5)
        assertTrue(list.none { it.name.contains("plank", ignoreCase = true) })
    }
    @Test fun requiredExercisesArePresentWithTheRightKind() {
        val kinds = list.associate { it.id to it.kind }
        val weights = listOf("squat", "bench-press", "bent-over-row", "dumbbell-shoulder-press", "lat-pulldown",
            "dumbbell-bicep-curl", "triceps-pushdown", "incline-dumbbell-press", "dumbbell-flyes",
            "overhead-triceps-extension", "seated-cable-row", "barbell-curl", "hammer-curl", "leg-press",
            "leg-extension", "lying-leg-curl", "side-lateral-raise")
        weights.forEach { assertEquals(it, ExerciseKind.WEIGHTS, kinds[it]) }
        listOf("pull-up", "chin-up", "push-up", "dips", "chest-dips").forEach { assertEquals(it, ExerciseKind.BODY_WEIGHT, kinds[it]) }
        assertEquals(ExerciseKind.REPS_ONLY, kinds["crunches"])
        listOf("treadmill", "stationary-bike").forEach { assertEquals(it, ExerciseKind.CARDIO, kinds[it]) }
    }
}
```

- [ ] **Step 2: Run unit tests and confirm it fails** (`BuiltInExercise` unresolved, then file not found)

- [ ] **Step 3: Create `BuiltInExercise.kt`, `selection.json` and the build script**

`package.json`: `{ "private": true, "type": "module", "dependencies": { "sharp": "0.35.5" } }`

`build-pack.mjs`, run from `tools/exercise-pack/`:
1. Read `selection.json`. Download the dataset index into `.cache/dataset.json` if it is not there.
2. Stop with a clear message if any `source` is missing from the dataset or has fewer than two images, or if two entries share an `id`.
3. For each entry, download `exercises/<source>/0.jpg` and `1.jpg` from the same repository into `.cache/` unless already cached, and write `sharp(file).resize({ width: 400 }).webp({ quality: 70 })` to `app/src/main/assets/exercises/<id>_0.webp` and `<id>_1.webp`.
4. Delete any `.webp` in that folder that is not in the selection.
5. Write `app/src/main/assets/exercises.json`: the entries without `source`, sorted by name ignoring case.

`README.md`: three lines saying what the folder is, how to run it (`npm install`, `node build-pack.mjs`) and that the two outputs are committed.

- [ ] **Step 4: Build the pack**

Run: `cd tools/exercise-pack && npm install && node build-pack.mjs`
Expected: it prints the number of exercises written and exits 0; `git status` shows `exercises.json` and about 300 `.webp` files.

- [ ] **Step 5: Run unit tests and confirm they pass**

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "Add built-in exercise list and picture pack"
```

---

### Task 5: Database, exercise repository and profile repository

**Files:**
- Create: `app/src/main/java/com/example/fit/data/db/{Entities,Daos,AppDatabase}.kt`
- Create: `app/src/main/java/com/example/fit/data/{ExerciseRepository,ProfileRepository}.kt`
- Create (generated, committed): `app/schemas/com.example.fit.data.db.AppDatabase/1.json`
- Test: `app/src/androidTest/java/com/example/fit/data/{TestSupport,ExerciseRepositoryTest,ProfileRepositoryTest}.kt`

**Interfaces:**
- Consumes: Task 1 models; `DEFAULT_AVERAGE_N`, `MIN_AVERAGE_N`, `MAX_AVERAGE_N` from Task 2.
- Produces:

All eleven tables are defined now, so the schema stays at version 1 for the whole plan. Later tasks add DAOs, never tables.

| Table (entity class) | Columns | Keys |
|---|---|---|
| `exercise` (`ExerciseEntity`) | `id` TEXT, `name`, `muscleGroup`, `muscle`?, `equipment`?, `kind`, `isCustom`, `isHidden`, `imageBase`? | PK `id` |
| `routine` (`RoutineEntity`) | `id` INTEGER autogenerate, `name`, `position` | PK `id` |
| `routine_exercise` (`RoutineExerciseEntity`) | `id` autogenerate, `routineId`, `exerciseId`, `position` | FK `routineId` → routine CASCADE; FK `exerciseId` → exercise; UNIQUE(`routineId`,`exerciseId`); index on `exerciseId` |
| `routine_set` (`RoutineSetEntity`) | `id` autogenerate, `routineExerciseId`, `position`, `weightHg`, `reps`, `minutes` | FK → routine_exercise CASCADE, indexed |
| `schedule_day` (`ScheduleDayEntity`) | `dayOfWeek` INTEGER 1 (Monday) to 7, `routineId`? | PK `dayOfWeek`; FK `routineId` → routine SET NULL, indexed |
| `workout` (`WorkoutEntity`) | `id` autogenerate, `date` TEXT ISO, `routineId`?, `routineName`, `status`, `bodyWeightHg`? | UNIQUE(`date`); FK `routineId` → routine SET NULL, indexed |
| `workout_exercise` (`WorkoutExerciseEntity`) | `id` autogenerate, `workoutId`, `exerciseId`, `position` | FK `workoutId` → workout CASCADE; FK `exerciseId` → exercise; UNIQUE(`workoutId`,`exerciseId`); index on `exerciseId` |
| `workout_set` (`WorkoutSetEntity`) | `id` autogenerate, `workoutExerciseId`, `position`, `weightHg`, `reps`, `minutes`, `done` | FK → workout_exercise CASCADE, indexed |
| `profile` (`ProfileEntity`) | `id` INTEGER always 1, `name`, `dateOfBirth`? TEXT ISO, `heightFeet`?, `heightInches`?, `welcomeDone` | PK `id` |
| `body_weight` (`BodyWeightEntity`) | `date` TEXT ISO, `weightHg` | PK `date` |
| `setting` (`SettingEntity`) | `key` TEXT, `value` TEXT | PK `key` |

Enums are stored by name; `LocalDate` through a type converter to ISO text.

```kotlin
// data/db/AppDatabase.kt
@Database(entities = [/* the eleven above */], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun profileDao(): ProfileDao
    companion object { fun create(context: Context, inMemory: Boolean = false): AppDatabase }  // file "overload.db"
}

// data/db/Entities.kt
fun Exercise.toEntity(): ExerciseEntity
fun ExerciseEntity.toDomain(): Exercise

// data/db/Daos.kt
interface ExerciseDao { @Upsert suspend fun upsertAll(exercises: List<ExerciseEntity>) /* plus whatever ExerciseRepository needs */ }
interface ProfileDao { /* profile row, body_weight, setting; getSetting(key): String?, putSetting(key, value) */ }

// data/ExerciseRepository.kt
class ExerciseRepository(db: AppDatabase) {
    fun observeAll(): Flow<List<Exercise>>        // hidden ones left out; sorted by name ignoring case
    suspend fun get(id: String): Exercise?         // finds hidden ones too
    suspend fun createCustom(name: String, group: MuscleGroup, kind: ExerciseKind): String   // id "custom-<uuid>", isCustom, no image
    suspend fun renameCustom(id: String, name: String)   // ignored for built-ins
    suspend fun deleteCustom(id: String)                 // ignored for built-ins; see below
}

// data/ProfileRepository.kt
class ProfileRepository(db: AppDatabase) {
    fun observeProfile(): Flow<Profile>                  // Profile() while no row exists
    suspend fun saveProfile(profile: Profile)
    fun observeBodyWeights(): Flow<List<BodyWeightEntry>>   // ascending by date
    suspend fun currentBodyWeightHg(): Int?              // the latest entry
    suspend fun setBodyWeight(date: LocalDate, weightHg: Int)
    fun observeAverageN(): Flow<Int>
    suspend fun setAverageN(n: Int)                      // coerced into MIN_AVERAGE_N..MAX_AVERAGE_N
}
```

`deleteCustom` (spec 5.1): remove the exercise from every routine; then delete its row if no `workout_exercise` refers to it, otherwise set `isHidden`.

`setBodyWeight` (spec 5.4, 5.12): one entry per date, a second call on the same date replaces it; in the same transaction, set `bodyWeightHg` on the `workout` row with that date, if there is one.

- [ ] **Step 1: Write the failing tests**

```kotlin
// TestSupport.kt
fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()

val TEST_EXERCISES = listOf(
    Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS, imageBase = "exercises/bench-press"),
    Exercise("pull-up", "Pull-up", MuscleGroup.BACK, "Lats", "body only", ExerciseKind.BODY_WEIGHT, imageBase = "exercises/pull-up"),
    Exercise("crunches", "Crunches", MuscleGroup.CORE, "Abdominals", "body only", ExerciseKind.REPS_ONLY, imageBase = "exercises/crunches"),
    Exercise("treadmill", "Treadmill", MuscleGroup.CARDIO, "Cardio", "machine", ExerciseKind.CARDIO, imageBase = "exercises/treadmill"),
)
suspend fun seedTestExercises(db: AppDatabase) = db.exerciseDao().upsertAll(TEST_EXERCISES.map { it.toEntity() })

// ExerciseRepositoryTest.kt
@RunWith(AndroidJUnit4::class)
class ExerciseRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ExerciseRepository
    @Before fun setUp() = runBlocking { db = inMemoryDb(); seedTestExercises(db); repo = ExerciseRepository(db) }
    @After fun tearDown() = db.close()

    @Test fun listsExercisesSortedByName() = runTest {
        assertEquals(listOf("Bench Press", "Crunches", "Pull-up", "Treadmill"), repo.observeAll().first().map { it.name })
        assertEquals(TEST_EXERCISES[0], repo.get("bench-press"))
    }
    @Test fun customExercise_createRenameDelete() = runTest {
        val id = repo.createCustom("Cable Y Raise", MuscleGroup.SHOULDERS, ExerciseKind.WEIGHTS)
        assertTrue(id.startsWith("custom-"))
        val created = repo.get(id)!!
        assertTrue(created.isCustom); assertNull(created.imageBase); assertEquals(MuscleGroup.SHOULDERS, created.group)
        repo.renameCustom(id, "Y Raise"); assertEquals("Y Raise", repo.get(id)!!.name)
        repo.deleteCustom(id); assertNull(repo.get(id))
    }
    @Test fun builtInExercisesCannotBeRenamedOrDeleted() = runTest {
        repo.renameCustom("bench-press", "X"); repo.deleteCustom("bench-press")
        assertEquals("Bench Press", repo.get("bench-press")!!.name)
    }
}

// ProfileRepositoryTest.kt
@RunWith(AndroidJUnit4::class)
class ProfileRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ProfileRepository
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    @Before fun setUp() { db = inMemoryDb(); repo = ProfileRepository(db) }
    @After fun tearDown() = db.close()

    @Test fun profileStartsEmptyThenSaves() = runTest {
        assertEquals(Profile(), repo.observeProfile().first())
        val sam = Profile("Sam", LocalDate.of(1996, 3, 14), 5, 9, welcomeDone = true)
        repo.saveProfile(sam); assertEquals(sam, repo.observeProfile().first())
    }
    @Test fun bodyWeightKeepsOneEntryPerDay() = runTest {
        assertNull(repo.currentBodyWeightHg())
        repo.setBodyWeight(d(9), 723); repo.setBodyWeight(d(9), 725); repo.setBodyWeight(d(10), 720)
        assertEquals(listOf(BodyWeightEntry(d(9), 725), BodyWeightEntry(d(10), 720)), repo.observeBodyWeights().first())
        assertEquals(720, repo.currentBodyWeightHg())
    }
    @Test fun averageNDefaultsToTenAndStaysInRange() = runTest {
        assertEquals(10, repo.observeAverageN().first())
        repo.setAverageN(5); assertEquals(5, repo.observeAverageN().first())
        repo.setAverageN(1); assertEquals(2, repo.observeAverageN().first())
        repo.setAverageN(99); assertEquals(20, repo.observeAverageN().first())
    }
}
```

- [ ] **Step 2: Run instrumented tests for both classes and confirm they fail** (compilation: `AppDatabase` unresolved)

- [ ] **Step 3: Implement entities, the two DAOs, `AppDatabase`, and the two repositories**

- [ ] **Step 4: Run instrumented tests and confirm they pass**

Also confirm `app/schemas/com.example.fit.data.db.AppDatabase/1.json` now exists.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add Room database with exercise and profile repositories"
```

---

### Task 6: Routines, starting sets and the weekly schedule

**Files:**
- Modify: `app/src/main/java/com/example/fit/data/db/Daos.kt` (add `RoutineDao`), `data/db/AppDatabase.kt` (add `abstract fun routineDao(): RoutineDao`)
- Create: `app/src/main/java/com/example/fit/data/{StartingSets,RoutineRepository}.kt`
- Test: `app/src/androidTest/java/com/example/fit/data/RoutineRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 5 database and `TestSupport`; `SetField`, `stepped` (Task 1); `defaultStartingSets` (Task 2).
- Produces:

```kotlin
// data/StartingSets.kt
class StartingSets(db: AppDatabase) {
    /** Spec 5.7 last bullet: the ticked sets of the exercise from the most recent FINISHED workout, in any routine,
     *  in which it has a ticked set; or defaultStartingSets(kind, equipment) if it has never been done. */
    suspend fun forExercise(exercise: Exercise): List<SetValues>
}

// data/RoutineRepository.kt
class RoutineRepository(db: AppDatabase, startingSets: StartingSets) {
    fun observeRoutines(): Flow<List<Routine>>           // in list order, each with its exercises and sets in order
    fun observeRoutine(id: Long): Flow<Routine?>
    suspend fun getRoutine(id: Long): Routine?
    suspend fun createRoutine(name: String): Long        // added at the end of the list
    suspend fun renameRoutine(id: Long, name: String)
    suspend fun duplicateRoutine(id: Long): Long         // "<name> copy", placed at the end
    suspend fun deleteRoutine(id: Long)
    suspend fun addExercise(routineId: Long, exerciseId: String)        // at the end, with StartingSets; no-op if already there
    suspend fun removeExercise(routineId: Long, exerciseId: String)
    suspend fun moveExercise(routineId: Long, fromIndex: Int, toIndex: Int)
    suspend fun adjustSet(routineId: Long, exerciseId: String, position: Int, field: SetField, steps: Int)
    suspend fun addSet(routineId: Long, exerciseId: String)             // copies the last set; no-op for CARDIO
    suspend fun removeSet(routineId: Long, exerciseId: String, position: Int)   // no-op when only one set is left
    fun observeSchedule(): Flow<Map<DayOfWeek, Long?>>   // always all seven days
    suspend fun setScheduleDay(day: DayOfWeek, routineId: Long?)
}
```

`adjustSet` reads the set, applies `stepped`, and writes it back inside one database transaction, so concurrent calls never lose a step. Positions are always 0, 1, 2, … with no gaps after any add, remove or move.

- [ ] **Step 1: Write the failing test**

```kotlin
@RunWith(AndroidJUnit4::class)
class RoutineRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RoutineRepository
    private suspend fun chest(): Long = repo.createRoutine("Chest").also {
        repo.addExercise(it, "bench-press"); repo.addExercise(it, "treadmill")
    }
    private suspend fun benchSets(id: Long) = repo.getRoutine(id)!!.exercises.first { it.exercise.id == "bench-press" }.sets

    @Before fun setUp() = runBlocking { db = inMemoryDb(); seedTestExercises(db); repo = RoutineRepository(db, StartingSets(db)) }
    @After fun tearDown() = db.close()

    @Test fun newExerciseGetsDefaultStartingSets_andIsNeverAddedTwice() = runTest {
        val id = chest(); repo.addExercise(id, "bench-press")
        val routine = repo.getRoutine(id)!!
        assertEquals(listOf("bench-press", "treadmill"), routine.exercises.map { it.exercise.id })
        assertEquals(List(3) { SetValues(200, 10) }, routine.exercises[0].sets)
        assertEquals(listOf(SetValues(minutes = 10)), routine.exercises[1].sets)
    }
    @Test fun setsCanBeAdjustedAddedAndRemoved() = runTest {
        val id = chest()
        repo.adjustSet(id, "bench-press", 1, SetField.WEIGHT, +5)
        assertEquals(listOf(200, 225, 200), benchSets(id).map { it.weightHg })
        repo.addSet(id, "bench-press"); assertEquals(4, benchSets(id).size); assertEquals(SetValues(200, 10), benchSets(id)[3])
        repo.removeSet(id, "bench-press", 0); assertEquals(listOf(225, 200, 200), benchSets(id).map { it.weightHg })
        repeat(5) { repo.removeSet(id, "bench-press", 0) }; assertEquals(1, benchSets(id).size)
        repo.addSet(id, "treadmill"); assertEquals(1, repo.getRoutine(id)!!.exercises[1].sets.size)
    }
    @Test fun adjustSet_isAtomic_underRapidSteps() = runBlocking {
        val id = chest()
        (1..50).map { async(Dispatchers.Default) { repo.adjustSet(id, "bench-press", 0, SetField.WEIGHT, +1) } }.awaitAll()
        assertEquals(200 + 50 * 5, benchSets(id)[0].weightHg)
    }
    @Test fun exercisesCanBeReorderedAndRemoved() = runTest {
        val id = chest(); repo.addExercise(id, "pull-up")
        repo.moveExercise(id, 0, 2)
        assertEquals(listOf("treadmill", "pull-up", "bench-press"), repo.getRoutine(id)!!.exercises.map { it.exercise.id })
        repo.removeExercise(id, "pull-up")
        assertEquals(listOf("treadmill", "bench-press"), repo.getRoutine(id)!!.exercises.map { it.exercise.id })
    }
    @Test fun duplicateIsAnIndependentCopy() = runTest {
        val id = chest(); val copyId = repo.duplicateRoutine(id)
        assertEquals("Chest copy", repo.getRoutine(copyId)!!.name)
        assertEquals(repo.getRoutine(id)!!.exercises, repo.getRoutine(copyId)!!.exercises)
        repo.adjustSet(copyId, "bench-press", 0, SetField.WEIGHT, +1)
        assertEquals(200, benchSets(id)[0].weightHg)
        assertEquals(listOf("Chest", "Chest copy"), repo.observeRoutines().first().map { it.name })
    }
    @Test fun renameChangesTheName() = runTest {
        val id = chest(); repo.renameRoutine(id, "Push"); assertEquals("Push", repo.observeRoutine(id).first()!!.name)
    }
    @Test fun scheduleCoversSevenDays_andDeletingARoutineFreesItsDays() = runTest {
        val id = chest()
        assertEquals(DayOfWeek.entries.associateWith { null }, repo.observeSchedule().first())
        repo.setScheduleDay(DayOfWeek.MONDAY, id); repo.setScheduleDay(DayOfWeek.THURSDAY, id)
        assertEquals(id, repo.observeSchedule().first()[DayOfWeek.MONDAY])
        repo.setScheduleDay(DayOfWeek.THURSDAY, null); assertNull(repo.observeSchedule().first()[DayOfWeek.THURSDAY])
        repo.deleteRoutine(id)
        assertNull(repo.getRoutine(id)); assertEquals(DayOfWeek.entries.associateWith { null }, repo.observeSchedule().first())
    }
}
```

- [ ] **Step 2: Run the instrumented test and confirm it fails** (compilation: `RoutineRepository` unresolved)

- [ ] **Step 3: Implement `RoutineDao`, `StartingSets` and `RoutineRepository`**

- [ ] **Step 4: Run the instrumented test and confirm it passes**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add routine repository with starting sets and weekly schedule"
```

---

### Task 7: Today's workout: creating it and changing it

**Files:**
- Modify: `app/src/main/java/com/example/fit/data/db/Daos.kt` (add `WorkoutDao`), `data/db/AppDatabase.kt` (add `abstract fun workoutDao(): WorkoutDao`)
- Create: `app/src/main/java/com/example/fit/data/WorkoutRepository.kt`
- Test: `app/src/androidTest/java/com/example/fit/data/WorkoutRepositoryTest.kt`

**Interfaces:**
- Consumes: Tasks 5 and 6 (`AppDatabase`, `StartingSets`, `RoutineRepository`, `ProfileRepository`, `ExerciseRepository`, `TestSupport`).
- Produces:

```kotlin
class WorkoutRepository(db: AppDatabase, startingSets: StartingSets) {
    fun observeWorkoutOn(date: LocalDate): Flow<Workout?>   // every set, ticked or not
    fun observeWorkout(id: Long): Flow<Workout?>

    /** The workout on `date`, creating it IN_PROGRESS from the routine's starting values if there is none. */
    suspend fun ensureWorkout(date: LocalDate, routineId: Long): Long
    /** Like ensureWorkout, but replaces an existing workout on `date` that has no ticked set.
     *  Throws IllegalStateException if the existing workout has a ticked set (spec 5.3). */
    suspend fun startWorkout(date: LocalDate, routineId: Long): Long

    suspend fun setDone(workoutId: Long, exerciseId: String, position: Int, done: Boolean)
    suspend fun adjustSet(workoutId: Long, exerciseId: String, position: Int, field: SetField, steps: Int)
    suspend fun addSet(workoutId: Long, exerciseId: String, done: Boolean = false)   // copies the last set; no-op for CARDIO
    suspend fun removeSet(workoutId: Long, exerciseId: String, position: Int)        // removes the exercise too if it was its last set
    suspend fun addExercise(workoutId: Long, exerciseId: String, alsoUpdateRoutine: Boolean)
    suspend fun removeExercise(workoutId: Long, exerciseId: String, alsoUpdateRoutine: Boolean)
    suspend fun replaceExercise(workoutId: Long, oldExerciseId: String, newExerciseId: String, alsoUpdateRoutine: Boolean)
    suspend fun deleteWorkout(workoutId: Long)
}
```

Rules:
- A new workout copies the routine's name, its exercises in order and their starting sets (all unticked), and `bodyWeightHg` = the latest body weight entry, or null.
- `adjustSet` is one transaction, as in Task 6.
- An exercise appears at most once in a workout: `addExercise` of one already present does nothing. A new exercise gets `StartingSets.forExercise`.
- `alsoUpdateRoutine` (spec 5.5), when the workout's routine still exists: add → the routine gets the exercise at its end with the same sets; remove → it is removed from the routine; replace → in the routine the new exercise takes the old one's place (or goes to the end if the old one was not there), unless the new one is already in the routine, in which case the old one is only removed.
- `replaceExercise` keeps the old exercise's position in the workout.
- Positions stay gap-free after every change.

- [ ] **Step 1: Write the failing test**

```kotlin
@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var routines: RoutineRepository
    private lateinit var workouts: WorkoutRepository
    private lateinit var profile: ProfileRepository
    private lateinit var exercises: ExerciseRepository
    private val monday = LocalDate.of(2026, 11, 9)
    private var routineId = 0L

    private suspend fun today() = workouts.observeWorkoutOn(monday).first()!!
    private suspend fun ids() = today().exercises.map { it.exercise.id }
    private suspend fun bench() = today().exercises.first { it.exercise.id == "bench-press" }.sets
    private suspend fun routineIds() = routines.getRoutine(routineId)!!.exercises.map { it.exercise.id }

    @Before fun setUp() = runBlocking {
        db = inMemoryDb(); seedTestExercises(db)
        val starting = StartingSets(db)
        routines = RoutineRepository(db, starting); workouts = WorkoutRepository(db, starting)
        profile = ProfileRepository(db); exercises = ExerciseRepository(db)
        routineId = routines.createRoutine("Chest")
        routines.addExercise(routineId, "bench-press"); routines.addExercise(routineId, "treadmill")
    }
    @After fun tearDown() = db.close()

    @Test fun ensureWorkout_createsFromTheRoutineOnce() = runTest {
        val id = workouts.ensureWorkout(monday, routineId)
        assertEquals(id, workouts.ensureWorkout(monday, routineId))
        val w = today()
        assertEquals(WorkoutStatus.IN_PROGRESS, w.status); assertEquals("Chest", w.routineName); assertEquals(routineId, w.routineId)
        assertEquals(listOf("bench-press", "treadmill"), ids())
        assertEquals(List(3) { WorkoutSet(it, SetValues(200, 10), false) }, bench())
        assertEquals(w, workouts.observeWorkout(id).first())
        routines.addExercise(routineId, "pull-up"); routines.adjustSet(routineId, "bench-press", 0, SetField.WEIGHT, +4)
        assertEquals(w, today())   // editing the routine does not change a workout already created from it (spec 5.5)
    }
    @Test fun ticksAndNumbersAreStored() = runTest {
        val id = workouts.ensureWorkout(monday, routineId)
        workouts.adjustSet(id, "bench-press", 0, SetField.WEIGHT, +1); workouts.adjustSet(id, "bench-press", 0, SetField.REPS, -2)
        workouts.setDone(id, "bench-press", 0, true)
        assertEquals(WorkoutSet(0, SetValues(205, 8), true), bench()[0])
        workouts.setDone(id, "bench-press", 0, false); assertFalse(bench()[0].done)
    }
    @Test fun adjustSet_isAtomic_underRapidSteps() = runBlocking {
        val id = workouts.ensureWorkout(monday, routineId)
        (1..50).map { async(Dispatchers.Default) { workouts.adjustSet(id, "bench-press", 0, SetField.WEIGHT, +1) } }.awaitAll()
        assertEquals(200 + 50 * 5, bench()[0].values.weightHg)
    }
    @Test fun setsCanBeAddedAndRemoved() = runTest {
        val id = workouts.ensureWorkout(monday, routineId)
        workouts.adjustSet(id, "bench-press", 2, SetField.WEIGHT, +3); workouts.addSet(id, "bench-press")
        assertEquals(WorkoutSet(3, SetValues(215, 10), false), bench()[3])
        workouts.removeSet(id, "bench-press", 0)
        assertEquals(listOf(0, 1, 2), bench().map { it.position }); assertEquals(215, bench()[1].values.weightHg)
        workouts.removeSet(id, "treadmill", 0); assertEquals(listOf("bench-press"), ids())
    }
    @Test fun startWorkout_replacesAnUntouchedWorkout_butRefusesOnceASetIsTicked() = runTest {
        val other = routines.createRoutine("Core").also { routines.addExercise(it, "crunches") }
        workouts.ensureWorkout(monday, routineId)
        val id = workouts.startWorkout(monday, other)
        assertEquals("Core", today().routineName); assertEquals(listOf("crunches"), ids())
        workouts.setDone(id, "crunches", 0, true)
        assertThrows(IllegalStateException::class.java) { runBlocking { workouts.startWorkout(monday, routineId) } }
        assertEquals("Core", today().routineName)
    }
    @Test fun addExercise_onlyToday_orAlsoToTheRoutine() = runTest {
        val id = workouts.ensureWorkout(monday, routineId)
        workouts.addExercise(id, "pull-up", alsoUpdateRoutine = false); workouts.addExercise(id, "pull-up", alsoUpdateRoutine = false)
        assertEquals(listOf("bench-press", "treadmill", "pull-up"), ids()); assertEquals(listOf("bench-press", "treadmill"), routineIds())
        workouts.addExercise(id, "crunches", alsoUpdateRoutine = true)
        assertEquals(listOf("bench-press", "treadmill", "crunches"), routineIds())
    }
    @Test fun removeAndReplaceExercise() = runTest {
        val id = workouts.ensureWorkout(monday, routineId)
        workouts.replaceExercise(id, "bench-press", "pull-up", alsoUpdateRoutine = false)
        assertEquals(listOf("pull-up", "treadmill"), ids()); assertEquals(listOf("bench-press", "treadmill"), routineIds())
        workouts.replaceExercise(id, "pull-up", "crunches", alsoUpdateRoutine = true)
        assertEquals(listOf("crunches", "treadmill"), ids()); assertEquals(listOf("bench-press", "treadmill", "crunches"), routineIds())
        workouts.removeExercise(id, "treadmill", alsoUpdateRoutine = true)
        assertEquals(listOf("crunches"), ids()); assertEquals(listOf("bench-press", "crunches"), routineIds())
    }
    @Test fun workoutRemembersBodyWeightOfItsDay() = runTest {
        profile.setBodyWeight(monday.minusDays(3), 700)
        workouts.ensureWorkout(monday, routineId); assertEquals(700, today().bodyWeightHg)
        profile.setBodyWeight(monday, 705); assertEquals(705, today().bodyWeightHg)
        profile.setBodyWeight(monday.plusDays(1), 720); assertEquals(705, today().bodyWeightHg)
    }
    @Test fun deletingACustomExerciseWithHistoryOnlyHidesIt() = runTest {
        val custom = exercises.createCustom("Y Raise", MuscleGroup.SHOULDERS, ExerciseKind.WEIGHTS)
        routines.addExercise(routineId, custom)
        val id = workouts.ensureWorkout(monday, routineId)
        exercises.deleteCustom(custom)
        assertTrue(exercises.get(custom)!!.isHidden); assertTrue(custom in ids()); assertFalse(custom in routineIds())
        assertTrue(exercises.observeAll().first().none { it.id == custom })
        workouts.deleteWorkout(id); assertNull(workouts.observeWorkoutOn(monday).first())
    }
    @Test fun ticks_surviveReopeningTheDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("persist-test.db")
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, "persist-test.db").build()
        var file = open(); seedTestExercises(file)
        val r = RoutineRepository(file, StartingSets(file)); val rid = r.createRoutine("Chest"); r.addExercise(rid, "bench-press")
        val w = WorkoutRepository(file, StartingSets(file)); val id = w.ensureWorkout(monday, rid)
        w.adjustSet(id, "bench-press", 0, SetField.WEIGHT, +1); w.setDone(id, "bench-press", 0, true)
        file.close()
        file = open()
        val set = WorkoutRepository(file, StartingSets(file)).observeWorkoutOn(monday).first()!!.exercises[0].sets[0]
        assertEquals(WorkoutSet(0, SetValues(205, 10), true), set)
        file.close(); context.deleteDatabase("persist-test.db")
    }
}
```

- [ ] **Step 2: Run the instrumented test and confirm it fails** (compilation: `WorkoutRepository` unresolved)

- [ ] **Step 3: Implement `WorkoutDao` and `WorkoutRepository`**

- [ ] **Step 4: Run the instrumented test and confirm it passes**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add workout repository for creating and changing today's workout"
```

---

### Task 8: Finishing, day close and saved history

**Files:**
- Modify: `app/src/main/java/com/example/fit/data/WorkoutRepository.kt`, `data/db/Daos.kt`
- Test: `app/src/androidTest/java/com/example/fit/data/WorkoutSavingTest.kt`

**Interfaces:**
- Consumes: Task 7; `nextStartingSets` (Task 2).
- Produces (added to `WorkoutRepository`):

```kotlin
/** Applies spec 5.7 to the workout's routine, then marks the workout FINISHED. Unticked sets stay, so it can be reopened. */
suspend fun finish(workoutId: Long)
/** Back to IN_PROGRESS (spec 5.6, "Continue workout"). */
suspend fun reopen(workoutId: Long)
/** Day close (spec 5.6) for every workout dated before `today`: an IN_PROGRESS one gets the same treatment as finish();
 *  then unticked sets are deleted, exercises left without sets are deleted, and a workout left empty is deleted.
 *  Running it again changes nothing. */
suspend fun closeDaysBefore(today: LocalDate)
/** History: FINISHED workouts, ascending by date, holding ticked sets only; exercises and workouts with none are left out. */
fun observeSaved(): Flow<List<Workout>>
```

Spec 5.7 applied on `finish`: skip entirely if the workout has no routine or the routine is gone. Otherwise, for each exercise of the workout that has a ticked set and is still in the routine, replace that routine exercise's sets with `nextStartingSets(routineSets, workoutSets)`. Never add an exercise to the routine and never remove one.

- [ ] **Step 1: Write the failing test**

Same `setUp`, fields and helpers as `WorkoutRepositoryTest` (copy them), plus:

```kotlin
    private val tuesday = monday.plusDays(1)
    private suspend fun routineBench() = routines.getRoutine(routineId)!!.exercises.first { it.exercise.id == "bench-press" }.sets
    /** Bench: set 0 at 20.5 kg ticked, set 1 ticked, set 2 changed to 9 reps but not ticked. Treadmill untouched. */
    private suspend fun trainMonday(): Long {
        val id = workouts.ensureWorkout(monday, routineId)
        workouts.adjustSet(id, "bench-press", 0, SetField.WEIGHT, +1); workouts.setDone(id, "bench-press", 0, true)
        workouts.setDone(id, "bench-press", 1, true); workouts.adjustSet(id, "bench-press", 2, SetField.REPS, -1)
        return id
    }

    @Test fun finish_savesTickedSetsOnly_andCarriesNumbersIntoTheRoutine() = runTest {
        val id = trainMonday(); workouts.finish(id)
        val saved = workouts.observeSaved().first().single()
        assertEquals(WorkoutStatus.FINISHED, saved.status); assertEquals(listOf("bench-press"), saved.exercises.map { it.exercise.id })
        assertEquals(listOf(SetValues(205, 10), SetValues(200, 10)), saved.exercises[0].sets.map { it.values })
        assertEquals(4050L, saved.loadHg())
        assertEquals(listOf(SetValues(205, 10), SetValues(200, 10), SetValues(200, 10)), routineBench())   // third keeps its old numbers
        assertEquals(listOf(SetValues(minutes = 10)), routines.getRoutine(routineId)!!.exercises[1].sets)
    }
    @Test fun reopen_bringsBackTheUntickedSets() = runTest {
        val id = trainMonday(); workouts.finish(id); workouts.reopen(id)
        assertEquals(WorkoutStatus.IN_PROGRESS, today().status); assertEquals(SetValues(200, 9), bench()[2].values)
        assertTrue(workouts.observeSaved().first().isEmpty())
        workouts.setDone(id, "bench-press", 2, true); workouts.finish(id)
        assertEquals(SetValues(200, 9), routineBench()[2])
    }
    @Test fun closeDaysBefore_savesYesterdayAndIsSafeToRepeat() = runTest {
        val id = trainMonday()
        workouts.closeDaysBefore(monday); assertEquals(WorkoutStatus.IN_PROGRESS, today().status)   // still its own day
        repeat(2) { workouts.closeDaysBefore(tuesday) }
        val closed = workouts.observeWorkout(id).first()!!
        assertEquals(WorkoutStatus.FINISHED, closed.status); assertEquals(listOf("bench-press"), closed.exercises.map { it.exercise.id })
        assertEquals(listOf(0, 1), closed.exercises[0].sets.map { it.position })
        assertEquals(listOf(SetValues(205, 10), SetValues(200, 10), SetValues(200, 10)), routineBench())
    }
    @Test fun closeDaysBefore_dropsAWorkoutWithNoTicks() = runTest {
        val id = workouts.ensureWorkout(monday, routineId); workouts.adjustSet(id, "bench-press", 0, SetField.WEIGHT, +4)
        workouts.closeDaysBefore(tuesday)
        assertNull(workouts.observeWorkoutOn(monday).first()); assertTrue(workouts.observeSaved().first().isEmpty())
        assertEquals(List(3) { SetValues(200, 10) }, routineBench())
    }
    @Test fun finish_afterRoutineDeleted_keepsTheWorkout() = runTest {
        val id = trainMonday(); routines.deleteRoutine(routineId); workouts.finish(id)
        val saved = workouts.observeSaved().first().single()
        assertNull(saved.routineId); assertEquals("Chest", saved.routineName); assertEquals(4050L, saved.loadHg())
    }
    @Test fun finish_afterExerciseRemovedFromRoutine_doesNotPutItBack() = runTest {
        val id = trainMonday(); routines.removeExercise(routineId, "bench-press"); workouts.finish(id)
        assertEquals(listOf("treadmill"), routines.getRoutine(routineId)!!.exercises.map { it.exercise.id })
        assertEquals(1, workouts.observeSaved().first().size)
    }
    @Test fun finish_afterRoutineSetsChanged_usesTheRoutinesCurrentSets() = runTest {
        val id = trainMonday(); routines.removeSet(routineId, "bench-press", 2); workouts.finish(id)
        assertEquals(listOf(SetValues(205, 10), SetValues(200, 10)), routineBench())   // unticked third had no routine position left
    }
    @Test fun savedWorkoutsAreInDateOrder_andExerciseAddedElsewhereStartsFromHistory() = runTest {
        workouts.finish(trainMonday())
        val later = workouts.ensureWorkout(tuesday, routineId); workouts.setDone(later, "treadmill", 0, true); workouts.finish(later)
        assertEquals(listOf(monday, tuesday), workouts.observeSaved().first().map { it.date })
        val other = routines.createRoutine("Full Body"); routines.addExercise(other, "bench-press")
        assertEquals(listOf(SetValues(205, 10), SetValues(200, 10)), routines.getRoutine(other)!!.exercises[0].sets)
    }
```

- [ ] **Step 2: Run the instrumented test and confirm it fails** (compilation: `finish` unresolved)

- [ ] **Step 3: Implement `finish`, `reopen`, `closeDaysBefore` and `observeSaved`**

- [ ] **Step 4: Run the instrumented tests of Tasks 5 to 8 and confirm they all pass**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add finishing, day close, carry-forward and saved history"
```

---

### Task 9: Seeding built-in exercises and starter routines

**Files:**
- Create: `app/src/main/java/com/example/fit/data/{Seeder,StarterData}.kt`
- Test: `app/src/androidTest/java/com/example/fit/data/SeederTest.kt`

**Interfaces:**
- Consumes: `BuiltInExercise` and `assets/exercises.json` (Task 4); `AppDatabase`, `ProfileDao` settings, `ExerciseDao.upsertAll` (Task 5); `RoutineRepository` (Task 6).
- Produces:

```kotlin
// data/StarterData.kt  (spec section 10)
val STARTER_ROUTINES: List<Pair<String, List<String>>> = listOf(
    "Full Body" to listOf("squat", "bench-press", "bent-over-row", "dumbbell-shoulder-press", "lat-pulldown", "dumbbell-bicep-curl", "triceps-pushdown"),
    "Chest + Triceps" to listOf("bench-press", "incline-dumbbell-press", "dumbbell-flyes", "triceps-pushdown", "overhead-triceps-extension"),
    "Back + Biceps" to listOf("lat-pulldown", "seated-cable-row", "bent-over-row", "barbell-curl", "hammer-curl"),
    "Leg + Shoulder" to listOf("squat", "leg-press", "leg-extension", "lying-leg-curl", "dumbbell-shoulder-press", "side-lateral-raise"),
    "Cardio" to listOf("treadmill", "stationary-bike"),
)
val STARTER_SCHEDULE: Map<DayOfWeek, String?> = mapOf(
    DayOfWeek.MONDAY to "Chest + Triceps", DayOfWeek.TUESDAY to "Back + Biceps", DayOfWeek.WEDNESDAY to "Leg + Shoulder",
    DayOfWeek.THURSDAY to "Cardio", DayOfWeek.FRIDAY to "Full Body", DayOfWeek.SATURDAY to null, DayOfWeek.SUNDAY to null,
)

// data/Seeder.kt
class Seeder(db: AppDatabase, readExercisesJson: () -> String) {
    suspend fun seedIfNeeded()
    companion object { const val SEED_VERSION = 1; const val SEED_VERSION_KEY = "seedVersion" }
}
```

`seedIfNeeded`, in one transaction:
1. If the stored `seedVersion` setting is not `SEED_VERSION`: upsert every built-in exercise from the JSON (`isCustom = false`, `imageBase = "exercises/<id>"`), then store `SEED_VERSION`. Custom exercises and user data are never touched.
2. If no `profile` row exists (first run): save `Profile()`, create the starter routines in order through `RoutineRepository` (so each exercise gets `defaultStartingSets`), and set the starter schedule.

- [ ] **Step 1: Write the failing test**

```kotlin
@RunWith(AndroidJUnit4::class)
class SeederTest {
    private val json = InstrumentationRegistry.getInstrumentation().targetContext.assets.open("exercises.json").bufferedReader().readText()
    private lateinit var db: AppDatabase
    private lateinit var routines: RoutineRepository
    private lateinit var exercises: ExerciseRepository
    @Before fun setUp() { db = inMemoryDb(); routines = RoutineRepository(db, StartingSets(db)); exercises = ExerciseRepository(db) }
    @After fun tearDown() = db.close()

    @Test fun firstRun_seedsExercisesRoutinesAndSchedule() = runTest {
        Seeder(db) { json }.seedIfNeeded()
        assertTrue(exercises.observeAll().first().size in 140..160)
        val benchPress = exercises.get("bench-press")!!
        assertEquals("exercises/bench-press", benchPress.imageBase); assertFalse(benchPress.isCustom)
        val all = routines.observeRoutines().first()
        assertEquals(listOf("Full Body", "Chest + Triceps", "Back + Biceps", "Leg + Shoulder", "Cardio"), all.map { it.name })
        val chest = all[1]
        assertEquals(listOf("bench-press", "incline-dumbbell-press", "dumbbell-flyes", "triceps-pushdown", "overhead-triceps-extension"),
            chest.exercises.map { it.exercise.id })
        assertEquals(List(3) { SetValues(200, 10) }, chest.exercises[0].sets)   // barbell
        assertEquals(List(3) { SetValues(50, 10) }, chest.exercises[1].sets)    // dumbbell
        assertEquals(List(3) { SetValues(100, 10) }, chest.exercises[3].sets)   // cable
        assertEquals(listOf(SetValues(minutes = 10)), all[4].exercises[0].sets)
        val schedule = routines.observeSchedule().first()
        assertEquals(chest.id, schedule[DayOfWeek.MONDAY]); assertEquals(all[4].id, schedule[DayOfWeek.THURSDAY])
        assertEquals(all[0].id, schedule[DayOfWeek.FRIDAY]); assertNull(schedule[DayOfWeek.SATURDAY]); assertNull(schedule[DayOfWeek.SUNDAY])
        assertEquals(Profile(), ProfileRepository(db).observeProfile().first())
    }
    @Test fun laterRuns_neverRecreateWhatTheUserDeleted() = runTest {
        Seeder(db) { json }.seedIfNeeded()
        routines.deleteRoutine(routines.observeRoutines().first()[0].id)
        Seeder(db) { json }.seedIfNeeded()
        assertEquals(4, routines.observeRoutines().first().size)
    }
    @Test fun newSeedVersion_refreshesBuiltInsAndKeepsCustomExercises() = runTest {
        Seeder(db) { json }.seedIfNeeded()
        val custom = exercises.createCustom("Y Raise", MuscleGroup.SHOULDERS, ExerciseKind.WEIGHTS)
        db.profileDao().putSetting(Seeder.SEED_VERSION_KEY, "0")
        Seeder(db) { json.replace("\"Bench Press\"", "\"Bench Press X\"") }.seedIfNeeded()
        assertEquals("Bench Press X", exercises.get("bench-press")!!.name); assertNotNull(exercises.get(custom))
        assertEquals(5, routines.observeRoutines().first().size)
    }
}
```

- [ ] **Step 2: Run the instrumented test and confirm it fails** (compilation: `Seeder` unresolved)

- [ ] **Step 3: Implement `StarterData.kt` and `Seeder.kt`**

- [ ] **Step 4: Run the instrumented test and confirm it passes**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Seed built-in exercises, starter routines and starter schedule"
```

---

### Task 10: Backup export and import

**Files:**
- Create: `app/src/main/java/com/example/fit/data/backup/{BackupModels,BackupCodec,BackupRepository}.kt`
- Test: `app/src/test/java/com/example/fit/data/backup/BackupCodecTest.kt`, `app/src/androidTest/java/com/example/fit/data/BackupRepositoryTest.kt`

**Interfaces:**
- Consumes: `AppDatabase` and its DAOs (Tasks 5 to 7); repositories for the tests.
- Produces:

```kotlin
// BackupModels.kt
const val BACKUP_FORMAT = "overload-backup"
const val BACKUP_VERSION = 1
@Serializable data class BackupFile(val format: String, val version: Int, val exportedAt: String, /* everything listed below */)
fun backupFileName(date: LocalDate): String          // "overload-backup-2026-11-12.json"

// BackupCodec.kt
class BackupException(val reason: Reason) : Exception(reason.name) { enum class Reason { NOT_A_BACKUP, NEWER_VERSION } }
object BackupCodec {
    fun encode(file: BackupFile): String
    /** Checks `format` and `version` before decoding the rest. Unknown keys are ignored. */
    fun decode(text: String): BackupFile             // throws BackupException
}

// BackupRepository.kt
class BackupRepository(db: AppDatabase) {
    suspend fun export(now: LocalDateTime): String
    /** Decodes first; only a valid file touches the database. Then, in one transaction, replaces all user data. */
    suspend fun import(text: String)                 // throws BackupException
}
```

The file holds (spec 5.13): the profile; body weight entries; settings except `seedVersion`; every custom exercise and every exercise referred to by a routine or workout (all `Exercise` fields); routines with their exercises and sets, with their ids and list order; the schedule; every workout with its exercises and all its sets including the `done` flag, its status, date, routine id, routine name and body weight. Dates are ISO text, enums are names, weights are hectograms.

Import keeps the routine and workout ids from the file. Built-in exercises already in the database are left as they are. An exercise in the file that the database does not have is inserted: a custom one as it was, any other one hidden and without a picture.

- [ ] **Step 1: Write the failing tests**

```kotlin
// BackupCodecTest.kt (JVM)
class BackupCodecTest {
    private fun reasonOf(text: String) =
        runCatching { BackupCodec.decode(text) }.exceptionOrNull().let { (it as BackupException).reason }
    @Test fun rejectsThingsThatAreNotBackups() {
        for (text in listOf("", "hello", "[1,2]", "{\"format\":\"something-else\",\"version\":1}", "{\"format\":\"overload-backup\",\"version\":1}"))
            assertEquals(text, BackupException.Reason.NOT_A_BACKUP, reasonOf(text))
    }
    @Test fun rejectsANewerVersion() =
        assertEquals(BackupException.Reason.NEWER_VERSION, reasonOf("{\"format\":\"overload-backup\",\"version\":2}"))
    @Test fun fileName() = assertEquals("overload-backup-2026-11-12.json", backupFileName(LocalDate.of(2026, 11, 12)))
}

// BackupRepositoryTest.kt (instrumented)
@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    private data class Snapshot(val profile: Profile, val weights: List<BodyWeightEntry>, val n: Int, val routines: List<Routine>,
                                val schedule: Map<DayOfWeek, Long?>, val saved: List<Workout>, val today: Workout?, val exercises: List<Exercise>)
    private val monday = LocalDate.of(2026, 11, 9)
    private val now = LocalDateTime.of(2026, 11, 10, 9, 0)
    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase

    private suspend fun snapshot(db: AppDatabase): Snapshot {
        val s = StartingSets(db); val r = RoutineRepository(db, s); val w = WorkoutRepository(db, s); val p = ProfileRepository(db)
        return Snapshot(p.observeProfile().first(), p.observeBodyWeights().first(), p.observeAverageN().first(), r.observeRoutines().first(),
            r.observeSchedule().first(), w.observeSaved().first(), w.observeWorkoutOn(monday.plusDays(1)).first(), ExerciseRepository(db).observeAll().first())
    }
    /** Profile, body weights, N = 5, a custom exercise, a routine on Monday, a finished Monday workout, an in-progress Tuesday workout. */
    private suspend fun fill(db: AppDatabase) {
        val s = StartingSets(db); val r = RoutineRepository(db, s); val w = WorkoutRepository(db, s); val p = ProfileRepository(db)
        p.saveProfile(Profile("Sam", LocalDate.of(1996, 3, 14), 5, 9, true)); p.setBodyWeight(monday, 723); p.setAverageN(5)
        val custom = ExerciseRepository(db).createCustom("Y Raise", MuscleGroup.SHOULDERS, ExerciseKind.WEIGHTS)
        val id = r.createRoutine("Chest"); listOf("bench-press", "pull-up", custom).forEach { r.addExercise(id, it) }
        r.setScheduleDay(DayOfWeek.MONDAY, id)
        val first = w.ensureWorkout(monday, id); w.setDone(first, "bench-press", 0, true); w.setDone(first, "pull-up", 1, true); w.finish(first)
        val second = w.ensureWorkout(monday.plusDays(1), id); w.setDone(second, custom, 0, true)
    }
    @Before fun setUp() = runBlocking {
        source = inMemoryDb(); target = inMemoryDb(); seedTestExercises(source); seedTestExercises(target); fill(source)
    }
    @After fun tearDown() { source.close(); target.close() }

    @Test fun exportThenImport_restoresEverything() = runTest {
        BackupRepository(target).import(BackupRepository(source).export(now))
        assertEquals(snapshot(source), snapshot(target))
    }
    @Test fun import_replacesWhatWasThere() = runTest {
        RoutineRepository(target, StartingSets(target)).createRoutine("Old")
        ProfileRepository(target).setBodyWeight(monday.minusDays(30), 800)
        BackupRepository(target).import(BackupRepository(source).export(now))
        assertEquals(snapshot(source), snapshot(target))
    }
    @Test fun import_rejectsBadFiles_andKeepsData() = runTest {
        val good = BackupRepository(source).export(now)
        val before = snapshot(source)
        for (bad in listOf("", "hello", good.dropLast(40), good.replaceFirst("\"version\":1", "\"version\":2"), good.replaceFirst("overload-backup", "x"))) {
            assertThrows(BackupException::class.java) { runBlocking { BackupRepository(source).import(bad) } }
            assertEquals(before, snapshot(source))
        }
    }
    @Test fun import_keepsHistoryOfExercisesThisAppDoesNotShip() = runTest {
        BackupRepository(target).import(BackupRepository(source).export(now).replace("pull-up", "retired-move"))
        val retired = ExerciseRepository(target).get("retired-move")!!
        assertTrue(retired.isHidden); assertNull(retired.imageBase)
        assertTrue(WorkoutRepository(target, StartingSets(target)).observeSaved().first().single().exercises.any { it.exercise.id == "retired-move" })
    }
}
```

`export` must write `"version":1` with no space after the colon (the default compact `Json` output), which the test relies on.

- [ ] **Step 2: Run both tests and confirm they fail** (compilation)

- [ ] **Step 3: Implement the three backup files**

- [ ] **Step 4: Run unit tests and the instrumented test and confirm they pass**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add backup export and import"
```

---

## Screens: shared rules for Tasks 11 to 18

- Layout and content follow the approved mockups in `docs/superpowers/specs/mockups/` and spec section 6. Exact colours and spacing are the implementer's.
- Compose tests assert on English text, test tags and content descriptions. The quoted strings in each task's tests are the exact strings to put in `strings.xml`.
- Compose tests wrap content in `OverloadTheme { }` and use `createComposeRule()`. They pass plain state objects and a small recording fake for the callbacks; they never touch the database.
- Every ViewModel is created through `overloadViewModel { container -> ... }` (Task 11) and exposes one `StateFlow` of UI state built with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.
- − / + touch targets and the tick are at least 48 dp.

---

### Task 11: App shell: container, theme, navigation and shared controls

**Files:**
- Create: `app/src/main/java/com/example/fit/{OverloadApp,AppContainer}.kt`, `data/TodayHolder.kt`, `ui/ViewModels.kt`, `ui/nav/{Routes,AppNav}.kt`, `ui/components/{StepButton,ExerciseImage,Common}.kt`
- Modify: `MainActivity.kt`, `ui/theme/{Color,Theme}.kt` (`FitTheme` becomes `OverloadTheme`), `AndroidManifest.xml` (`android:name=".OverloadApp"`), `res/values/strings.xml`, `res/values/themes.xml` (no change of parent needed)
- Create: `res/drawable/ic_tab_{today,routines,progress,history,profile}.xml`, `ic_drag_handle.xml`, `ic_muscle_{chest,back,shoulders,arms,legs,core,cardio}.xml` (simple original 24 dp vector icons, single colour, tinted by the theme)
- Test: `app/src/test/java/com/example/fit/ui/components/StepRepeatTest.kt`, `app/src/androidTest/java/com/example/fit/ui/{TestApp,StepButtonTest,ExerciseImageTest,AppNavTest}.kt`

**Interfaces:**
- Consumes: all repositories and the `Seeder` (Tasks 5 to 10); `appDay`, `AppClock` (Task 1).
- Produces:

```kotlin
// data/TodayHolder.kt
class TodayHolder(clock: AppClock) { val date: StateFlow<LocalDate>; fun refresh() }   // date = appDay(clock.now())

// AppContainer.kt
class AppContainer(context: Context, inMemory: Boolean = false, val clock: AppClock = AppClock { LocalDateTime.now() }) {
    val db: AppDatabase
    val exercises: ExerciseRepository; val profile: ProfileRepository; val routines: RoutineRepository
    val workouts: WorkoutRepository; val backup: BackupRepository; val seeder: Seeder   // reads assets/exercises.json
    val today: TodayHolder
    val ready: StateFlow<Boolean>
    suspend fun initialize()      // seeder.seedIfNeeded(); onForeground(); ready = true
    suspend fun onForeground()    // today.refresh(); workouts.closeDaysBefore(today.date.value)
}
// OverloadApp.kt
class OverloadApp : Application() { lateinit var container: AppContainer }   // onCreate: build it, launch initialize() on an app scope

// ui/ViewModels.kt
@Composable inline fun <reified VM : ViewModel> overloadViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM

// ui/nav/Routes.kt
@Serializable object WelcomeRoute; @Serializable object TodayRoute; @Serializable object RoutinesRoute
@Serializable object ProgressRoute; @Serializable object HistoryRoute; @Serializable object ProfileRoute
@Serializable data class RoutineEditorRoute(val routineId: Long)
@Serializable data class ExercisePickerRoute(val routineId: Long? = null, val workoutId: Long? = null)   // whose exercises to show as "Added"
@Serializable data class ExerciseDetailRoute(val exerciseId: String)
@Serializable data class WorkoutDetailRoute(val workoutId: Long)
const val PICKED_EXERCISE_KEY = "pickedExerciseId"   // result the picker leaves in the previous entry's SavedStateHandle

// ui/nav/AppNav.kt
@Composable fun AppNav()

// ui/theme/Theme.kt
@Composable fun OverloadTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)
data class OverloadColors(val done: Color, val recordContainer: Color, val onRecordContainer: Color, val chartSurface: Color,
    val chartSession: Color, val chartLoad: Color, val chartCardio: Color, val chartGrid: Color, val chartBaseline: Color, val chartLabel: Color)
val LocalOverloadColors: ProvidableCompositionLocal<OverloadColors>

// ui/components/StepButton.kt
/** Milliseconds until the next repeat for a button held this long, or null while it should not repeat yet. */
fun repeatIntervalMs(heldMs: Long): Long?        // null below 400; 150 from 400; 50 from 1500
@Composable fun StepButton(symbol: String, contentDescription: String, onStep: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)
@Composable fun NumberStepper(text: String, decreaseDescription: String, increaseDescription: String, onStep: (Int) -> Unit,
                              modifier: Modifier = Modifier, unit: String? = null, canDecrease: Boolean = true, canIncrease: Boolean = true)

// ui/components/ExerciseImage.kt
val LocalFlipFrame: ProvidableCompositionLocal<Int>   // 0 or 1; AppNav flips it every 1000 ms; default 0
/** Built-in: the asset "<imageBase>_<frame>.webp", tag "exercise-image". Custom: the muscle-group icon, tag "exercise-icon". */
@Composable fun ExerciseImage(exercise: Exercise, modifier: Modifier = Modifier)

// ui/components/Common.kt
@Composable fun KindTag(kind: ExerciseKind)   // "body weight", "reps only", "cardio"; nothing for WEIGHTS
@Composable fun StatTile(label: String, value: String, note: String?, modifier: Modifier = Modifier)
@Composable fun SectionLabel(text: String, modifier: Modifier = Modifier)
```

Colours (`dynamicColor` is gone):

| Role | Light | Dark |
|---|---|---|
| primary / onPrimary | `#3C4BC0` / `#FFFFFF` | `#B4BDFF` / `#1A237E` |
| primaryContainer / onPrimaryContainer | `#ECEEFB` / `#1B2470` | `#2B3594` / `#DEE0FF` |
| background / surface | `#F5F4FA` / `#FFFFFF` | `#121217` / `#1D1C23` |
| surfaceVariant / onSurfaceVariant | `#EEEDF6` / `#6B6A75` | `#2A2933` / `#A8A6B3` |
| onBackground, onSurface | `#1C1B1F` | `#E6E4EE` |
| outline / outlineVariant | `#CFCDD9` / `#E3E1EC` | `#474552` / `#34323D` |
| `done` | `#2E9E5B` | `#4CC27F` |
| `recordContainer` / `onRecordContainer` | `#FFF3CD` / `#8A5A00` | `#4A3A00` / `#FFD978` |
| `chartSurface` | `#FCFCFB` | `#1A1A19` |
| chart session, load, cardio, grid, baseline, label | as in Global Constraints | as in Global Constraints |

`AppNav`:
- Shows an empty themed surface until `container.ready` is true.
- Start destination: `WelcomeRoute` if `profile.welcomeDone` is false, otherwise `TodayRoute`.
- A bottom `NavigationBar` with five items, visible only on the five tab routes. Items carry test tags `tab-today`, `tab-routines`, `tab-progress`, `tab-history`, `tab-profile` and labels "Today", "Routines", "Progress", "History", "Profile".
- In this task every destination is a placeholder: an empty box with test tag `screen-today`, `screen-routines`, `screen-progress`, `screen-history`, `screen-profile`, `screen-welcome`, and so on. Later tasks replace a placeholder with the real screen and keep the tag on its root.
- `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)` calls `container.onForeground()`. This is what moves the app to a new day (spec 5.6).
- Provides `LocalFlipFrame`.

`StepButton`: a press calls `onStep` once at once; while held it repeats on the schedule of `repeatIntervalMs`; releasing or leaving the button stops it.

`ExerciseImage` decodes on a background dispatcher and keeps decoded bitmaps in a small in-memory cache keyed by asset path.

- [ ] **Step 1: Write the failing tests**

```kotlin
// StepRepeatTest.kt (JVM)
class StepRepeatTest {
    @Test fun schedule() {
        assertNull(repeatIntervalMs(0)); assertNull(repeatIntervalMs(399))
        assertEquals(150L, repeatIntervalMs(400)); assertEquals(150L, repeatIntervalMs(1499))
        assertEquals(50L, repeatIntervalMs(1500)); assertEquals(50L, repeatIntervalMs(60_000))
    }
}

// TestApp.kt (androidTest; shared by AppNavTest and Task 19)
class MutableClock(var now: LocalDateTime) : AppClock { override fun now() = now }
/** Swaps in an in-memory container with a controllable clock, then launches the real activity. */
fun launchApp(clock: MutableClock, prepare: suspend (AppContainer) -> Unit = {}): ActivityScenario<MainActivity> {
    val app = ApplicationProvider.getApplicationContext<OverloadApp>()
    val container = AppContainer(app, inMemory = true, clock = clock)
    runBlocking { container.seeder.seedIfNeeded(); prepare(container); container.initialize() }
    app.container = container
    return ActivityScenario.launch(MainActivity::class.java)
}

// StepButtonTest.kt
class StepButtonTest {
    @get:Rule val rule = createComposeRule()
    @Test fun tapStepsOnce() {
        var steps = 0
        rule.setContent { OverloadTheme { StepButton("+", "Increase weight", onStep = { steps++ }) } }
        rule.onNodeWithContentDescription("Increase weight").performClick()
        assertEquals(1, steps)
    }
    @Test fun holdingRepeatsThenSpeedsUp_andStopsOnRelease() {
        var steps = 0
        rule.setContent { OverloadTheme { StepButton("+", "Increase weight", onStep = { steps++ }) } }
        rule.mainClock.autoAdvance = false
        val button = rule.onNodeWithContentDescription("Increase weight")
        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(350); assertEquals(1, steps)
        rule.mainClock.advanceTimeBy(1150); assertTrue("$steps", steps in 6..10)     // slow repeats until 1.5 s
        rule.mainClock.advanceTimeBy(1000); assertTrue("$steps", steps in 22..32)    // fast repeats after
        button.performTouchInput { up() }
        val atRelease = steps; rule.mainClock.advanceTimeBy(1000); assertEquals(atRelease, steps)
    }
    @Test fun numberStepperReportsDirection() {
        val seen = mutableListOf<Int>()
        rule.setContent { OverloadTheme { NumberStepper("40.0", "Decrease weight", "Increase weight", onStep = { seen += it }, unit = "kg") } }
        rule.onNodeWithText("40.0").assertIsDisplayed()
        rule.onNodeWithContentDescription("Decrease weight").performClick(); rule.onNodeWithContentDescription("Increase weight").performClick()
        assertEquals(listOf(-1, +1), seen)
    }
}

// ExerciseImageTest.kt
class ExerciseImageTest {
    @get:Rule val rule = createComposeRule()
    private val builtIn = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS, imageBase = "exercises/bench-press")
    @Test fun builtInShowsItsPicture_customShowsAnIcon() {
        rule.setContent { OverloadTheme { Column {
            ExerciseImage(builtIn, Modifier.size(56.dp)); ExerciseImage(builtIn.copy(id = "custom-1", isCustom = true, imageBase = null), Modifier.size(56.dp))
        } } }
        rule.onNodeWithTag("exercise-image").assertIsDisplayed(); rule.onNodeWithTag("exercise-icon").assertIsDisplayed()
    }
}

// AppNavTest.kt
@RunWith(AndroidJUnit4::class)
class AppNavTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val clock = MutableClock(LocalDateTime.of(2026, 11, 9, 10, 0))
    @Test fun firstLaunchOpensWelcome() {
        launchApp(clock).use { compose.onNodeWithTag("screen-welcome").assertIsDisplayed() }
    }
    @Test fun showsFiveTabsAndSwitchesBetweenThem() {
        launchApp(clock) { it.profile.saveProfile(Profile(welcomeDone = true)) }.use {
            compose.onNodeWithTag("screen-today").assertIsDisplayed()
            mapOf("tab-today" to "Today", "tab-routines" to "Routines", "tab-progress" to "Progress",
                  "tab-history" to "History", "tab-profile" to "Profile").forEach { (tag, label) ->
                compose.onNodeWithTag(tag).assertTextEquals(label)
            }
            compose.onNodeWithTag("tab-history").performClick(); compose.onNodeWithTag("screen-history").assertIsDisplayed()
            compose.onNodeWithTag("tab-profile").performClick(); compose.onNodeWithTag("screen-profile").assertIsDisplayed()
        }
    }
}
```

- [ ] **Step 2: Run the unit test and the three instrumented tests and confirm they fail** (compilation)

- [ ] **Step 3: Implement the files listed above**

- [ ] **Step 4: Run them again and confirm they pass**; also run all unit tests

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add app container, theme, navigation shell and shared controls"
```

---

### Task 12: Chart components

**Files:**
- Create: `app/src/main/java/com/example/fit/ui/components/{ChartLayout,Charts}.kt`
- Modify: `res/values/strings.xml`
- Test: `app/src/test/java/com/example/fit/ui/components/ChartLayoutTest.kt`, `app/src/androidTest/java/com/example/fit/ui/ChartsTest.kt`

**Interfaces:**
- Consumes: `SessionSeries`, `ChartPoint`, `Measure`, `niceTicks` (Task 3); `formatKg`, `formatAxis`, `formatDayShort` (Task 1); `MIN_AVERAGE_N`, `MAX_AVERAGE_N` (Task 2); `OverloadTheme`, `LocalOverloadColors`, `StepButton` (Task 11).
- Produces:

```kotlin
// ChartLayout.kt (pure)
class PlotScale(val ticks: List<Double>) {        // ticks ascending, at least two
    fun y(value: Double, top: Float, bottom: Float): Float   // ticks.first() maps to bottom, ticks.last() to top
}
/** Index of the x nearest to `x`, or null for an empty list. */
fun nearestIndex(xs: List<Float>, x: Float): Int?
/** x positions for `count` marks: spread evenly across `width` when they fit at `minSpacing`, otherwise `minSpacing` apart. */
fun markPositions(count: Int, width: Float, minSpacing: Float, inset: Float): List<Float>
/** "965 kg", "35 reps", "25 min" */
fun formatMeasure(measure: Measure, value: Double): String

// Charts.kt
@Composable fun SessionChart(series: SessionSeries, n: Int, modifier: Modifier = Modifier)
data class ColumnDatum(val label: String, val value: Double, val tooltip: String, val muted: Boolean = false, val note: String? = null)
@Composable fun ColumnChart(title: String, columns: List<ColumnDatum>, color: Color, modifier: Modifier = Modifier)
@Composable fun TrendChart(title: String, points: List<ChartPoint>, valueText: (Double) -> String, modifier: Modifier = Modifier)
@Composable fun AverageNControl(n: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier)
```

What each draws (spec 5.10, mockup 03):
- All three charts: a card on `chartSurface` with the title; thin solid grid lines at the `niceTicks` values with `formatAxis` labels in a fixed gutter on the left; the plot beside the gutter with test tag `chart-plot`. When the marks do not fit at their minimum spacing (28 dp for sessions, 34 dp for columns) the plot scrolls sideways and starts scrolled to the newest mark; the gutter does not scroll. Date labels under the first, middle and last mark.
- A tap on the plot selects the nearest mark and shows its readout as a `Text` in the card's header row. With an empty list the card shows "No workouts yet" and no plot.
- `SessionChart`: title by measure ("Load per session (kg)", "Reps per session", "Minutes per session"); a legend row "Each session" (grey dot) and "Average of last N" (blue line, with the real N); grey dots joined by a thin grey line; the moving average as a 2 dp blue line ending in a dot with its value written beside it; readout "9 Nov · 965 kg". The y-range covers both the points and the average. The plot's content description is "3 sessions, latest 965 kg, average 987.5 kg".
- `ColumnChart`: columns at most 24 dp wide, rounded top, square at the baseline, baseline at zero; `muted` columns drawn at 45 % opacity with their `note` above them; the tallest column carries its value (`formatAxis`); readout is the datum's `tooltip`.
- `TrendChart`: a 2 dp line with a 10 % wash beneath, an end dot and the last value beside it; readout "10 Nov · " + `valueText(value)`.
- `AverageNControl`: "Average of last [−] N [+] sessions"; buttons described "Fewer sessions" and "More sessions"; − is disabled at 2 and + at 20.

- [ ] **Step 1: Write the failing tests**

```kotlin
// ChartLayoutTest.kt (JVM)
class ChartLayoutTest {
    @Test fun scaleMapsValuesBetweenBottomAndTop() {
        val scale = PlotScale(listOf(600.0, 800.0, 1000.0, 1200.0))
        assertEquals(100f, scale.y(600.0, top = 0f, bottom = 100f), 0.001f)
        assertEquals(0f, scale.y(1200.0, top = 0f, bottom = 100f), 0.001f)
        assertEquals(50f, scale.y(900.0, top = 0f, bottom = 100f), 0.001f)
    }
    @Test fun nearestMark() {
        assertEquals(1, nearestIndex(listOf(10f, 40f, 70f), 52f)); assertEquals(2, nearestIndex(listOf(10f, 40f, 70f), 900f))
        assertNull(nearestIndex(emptyList(), 5f))
    }
    @Test fun marksSpreadWhenTheyFit_andKeepMinimumSpacingWhenTheyDoNot() {
        assertEquals(listOf(10f, 150f, 290f), markPositions(3, width = 300f, minSpacing = 28f, inset = 10f))
        assertEquals(listOf(10f, 38f, 66f, 94f), markPositions(4, width = 60f, minSpacing = 28f, inset = 10f))
        assertEquals(listOf(150f), markPositions(1, width = 300f, minSpacing = 28f, inset = 10f))
    }
    @Test fun measureText() {
        assertEquals("965 kg", formatMeasure(Measure.LOAD_KG, 965.0)); assertEquals("987.5 kg", formatMeasure(Measure.LOAD_KG, 987.5))
        assertEquals("35 reps", formatMeasure(Measure.REPS, 35.0)); assertEquals("25 min", formatMeasure(Measure.MINUTES, 25.0))
    }
}

// ChartsTest.kt
class ChartsTest {
    @get:Rule val rule = createComposeRule()
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private val series = SessionSeries(Measure.LOAD_KG,
        listOf(ChartPoint(d(2), 740.0), ChartPoint(d(5), 1010.0), ChartPoint(d(9), 965.0)), listOf(740.0, 875.0, 987.5))

    @Test fun emptyHistory_showsAMessageInsteadOfAPlot() {
        rule.setContent { OverloadTheme { Column {
            SessionChart(SessionSeries(Measure.LOAD_KG, emptyList(), emptyList()), n = 10)
            ColumnChart("Total load per week (kg)", emptyList(), Color.Blue)
            TrendChart("Body weight (kg)", emptyList(), valueText = { "$it" })
        } } }
        rule.onAllNodesWithText("No workouts yet").assertCountEquals(3); rule.onNodeWithTag("chart-plot").assertDoesNotExist()
    }
    @Test fun sessionChartShowsTitleLegendAndSummary() {
        rule.setContent { OverloadTheme { SessionChart(series, n = 10) } }
        rule.onNodeWithText("Load per session (kg)").assertIsDisplayed()
        rule.onNodeWithText("Each session").assertIsDisplayed(); rule.onNodeWithText("Average of last 10").assertIsDisplayed()
        rule.onNodeWithTag("chart-plot").assert(hasContentDescription("3 sessions, latest 965 kg, average 987.5 kg"))
    }
    @Test fun tappingTheNewestMarkShowsItsReadout() {
        rule.setContent { OverloadTheme { SessionChart(series, n = 10) } }
        rule.onNodeWithTag("chart-plot").performTouchInput { click(centerRight) }
        rule.onNodeWithText("9 Nov · 965 kg").assertIsDisplayed()
    }
    @Test fun columnAndTrendChartsShowReadouts() {
        val columns = listOf(ColumnDatum("2 Nov", 1410.0, "Week of 2 Nov · 1,410 kg"),
            ColumnDatum("9 Nov", 500.0, "This week so far · 500 kg", muted = true, note = "so far"))
        rule.setContent { OverloadTheme { Column {
            ColumnChart("Total load per week (kg)", columns, LocalOverloadColors.current.chartLoad, Modifier.testTag("columns"))
            TrendChart("Body weight (kg)", listOf(ChartPoint(d(9), 72.5), ChartPoint(d(10), 72.3)), valueText = { "$it kg" }, Modifier.testTag("trend"))
        } } }
        rule.onNode(hasTestTag("chart-plot") and hasAnyAncestor(hasTestTag("columns"))).performTouchInput { click(centerRight) }
        rule.onNodeWithText("This week so far · 500 kg").assertIsDisplayed()
        rule.onNode(hasTestTag("chart-plot") and hasAnyAncestor(hasTestTag("trend"))).performTouchInput { click(centerRight) }
        rule.onNodeWithText("10 Nov · 72.3 kg").assertIsDisplayed()
    }
    @Test fun averageControlStaysBetweenTwoAndTwenty() {
        val changes = mutableListOf<Int>(); var n by mutableStateOf(19)
        rule.setContent { OverloadTheme { AverageNControl(n, onChange = { changes += it; n = it }) } }
        repeat(2) { rule.onNodeWithContentDescription("More sessions").performClick() }
        rule.onNodeWithContentDescription("Fewer sessions").performClick()
        assertEquals(listOf(20, 19), changes)
    }
}
```

- [ ] **Step 2: Run both tests and confirm they fail** (compilation)

- [ ] **Step 3: Implement `ChartLayout.kt` and `Charts.kt`**

- [ ] **Step 4: Run both tests and confirm they pass**

- [ ] **Step 5: Look at the charts.** Add a `@Preview` for each chart with the mockup's data (the 14-session series from `MovingAverageTest`, eight weeks of columns), in light and dark, and compare with `mockups/03-progress-history-profile.png`: no clipped labels, the end label does not collide with the last dot, grid lines are quiet.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "Add session, column and trend charts"
```

---

### Task 13: Profile, welcome and backup screens

**Files:**
- Create: `app/src/main/java/com/example/fit/ui/profile/{ProfileViewModel,ProfileScreen,WelcomeScreen}.kt`
- Modify: `ui/nav/AppNav.kt` (replace the `screen-profile` and `screen-welcome` placeholders), `res/values/strings.xml`
- Test: `app/src/androidTest/java/com/example/fit/ui/ProfileContentTest.kt`

**Interfaces:**
- Consumes: `ProfileRepository`, `BackupRepository`, `BackupException`, `backupFileName` (Tasks 5, 10); `TodayHolder`, `overloadViewModel`, `StepButton` (Task 11); `TrendChart` (Task 12); `parseKgToHg`, `ageOn`, `formatStepWeight` (Task 1).
- Produces:

```kotlin
data class ProfileUiState(
    val profile: Profile = Profile(), val age: Int? = null,
    val bodyWeightText: String = "", val bodyWeightValid: Boolean = true,
    val bodyWeights: List<BodyWeightEntry> = emptyList(),
)
interface ProfileActions {
    fun setName(name: String); fun setDateOfBirth(date: LocalDate?); fun setHeight(feet: Int?, inches: Int?)
    fun setBodyWeightText(text: String)   // stored as today's body weight whenever parseKgToHg(text) is not null
    fun nudgeBodyWeight(steps: Int)       // ±0.1 kg from the current body weight; nothing when there is none
    fun exportBackup(); fun importBackup()
}
class ProfileViewModel(profile: ProfileRepository, backup: BackupRepository, today: TodayHolder, clock: AppClock) : ViewModel()
@Composable fun ProfileScreen()
@Composable fun ProfileContent(state: ProfileUiState, actions: ProfileActions, modifier: Modifier = Modifier)
@Composable fun WelcomeContent(state: ProfileUiState, actions: ProfileActions, onSkip: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier)
```

Behaviour (spec 5.12, 5.13, mockup 03 screen 5):
- Name is a text field. Date of birth opens a Material date picker (no future dates) and shows "30 years" beside it. Height is two dropdowns, feet 3 to 8 shown as "5 ft" and inches 0 to 11 shown as "9 in". Every change is saved at once.
- Body weight is a decimal text field with test tag `body-weight-field`, with − and + beside it described "Decrease body weight" and "Increase body weight". While the text does not parse, the field shows "Enter a weight from 20 to 400 kg" and nothing is saved.
- The body weight `TrendChart` titled "Body weight (kg)" sits under the fields.
- "Export backup file" opens the system create-document screen with `backupFileName(today)` and writes `backup.export(clock.now())`. "Import backup file" opens the system open-document screen, reads the chosen file as text, asks "Replace everything in the app with this backup? This cannot be undone." with "Replace" and "Cancel", then imports. Results are shown in a snackbar: "Backup saved", "Backup restored", "That file is not an Overload backup", "That backup was made by a newer version of the app".
- At the bottom: "Exercise pictures: free-exercise-db (public domain)".
- Welcome shows the same four fields under the heading "Welcome to Overload", with "Skip" and "Done". Both set `welcomeDone = true` and go to Today with the welcome screen removed from the back stack.

- [ ] **Step 1: Write the failing test**

```kotlin
class ProfileContentTest {
    @get:Rule val rule = createComposeRule()
    private class Recorder : ProfileActions {
        val texts = mutableListOf<String>(); val nudges = mutableListOf<Int>(); var exports = 0; var imports = 0; var name = ""
        override fun setName(name: String) { this.name = name }
        override fun setDateOfBirth(date: LocalDate?) {}
        override fun setHeight(feet: Int?, inches: Int?) {}
        override fun setBodyWeightText(text: String) { texts += text }
        override fun nudgeBodyWeight(steps: Int) { nudges += steps }
        override fun exportBackup() { exports++ }
        override fun importBackup() { imports++ }
    }
    private val actions = Recorder()
    private val sam = ProfileUiState(Profile("Sam", LocalDate.of(1996, 3, 14), 5, 9, true), age = 30, bodyWeightText = "72.5")

    @Test fun showsTheProfileAndPassesChangesOn() {
        rule.setContent { OverloadTheme { ProfileContent(sam, actions) } }
        rule.onNodeWithText("Sam").assertIsDisplayed(); rule.onNodeWithText("30 years").assertIsDisplayed()
        rule.onNodeWithText("5 ft").assertIsDisplayed(); rule.onNodeWithText("9 in").assertIsDisplayed()
        rule.onNodeWithTag("body-weight-field").performTextReplacement("73.1"); assertEquals("73.1", actions.texts.last())
        rule.onNodeWithContentDescription("Increase body weight").performClick(); assertEquals(listOf(1), actions.nudges)
    }
    @Test fun invalidBodyWeightShowsAHint() {
        rule.setContent { OverloadTheme { ProfileContent(sam.copy(bodyWeightText = "7", bodyWeightValid = false), actions) } }
        rule.onNodeWithText("Enter a weight from 20 to 400 kg").assertIsDisplayed()
    }
    @Test fun backupButtonsAndCredits() {
        rule.setContent { OverloadTheme { ProfileContent(sam, actions) } }
        rule.onNodeWithText("Export backup file").performScrollTo().performClick()
        rule.onNodeWithText("Import backup file").performScrollTo().performClick()
        assertEquals(1, actions.exports); assertEquals(1, actions.imports)
        rule.onNodeWithText("Exercise pictures: free-exercise-db (public domain)").assertExists()
    }
    @Test fun welcomeOffersSkipAndDone() {
        var skipped = false; var done = false
        rule.setContent { OverloadTheme { WelcomeContent(ProfileUiState(), actions, onSkip = { skipped = true }, onDone = { done = true }) } }
        rule.onNodeWithText("Welcome to Overload").assertIsDisplayed()
        rule.onNodeWithText("Skip").performClick(); rule.onNodeWithText("Done").performClick()
        assertTrue(skipped && done)
    }
}
```

- [ ] **Step 2: Run the instrumented test and confirm it fails** (compilation)

- [ ] **Step 3: Implement the ViewModel, the two screens and the navigation wiring**

- [ ] **Step 4: Run it and `AppNavTest` and confirm they pass**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add profile, welcome and backup screens"
```

---

### Task 14: Routines tab and weekly schedule

**Files:**
- Create: `app/src/main/java/com/example/fit/ui/routines/{RoutinesViewModel,RoutinesScreen}.kt`
- Modify: `ui/nav/AppNav.kt` (replace the `screen-routines` placeholder), `res/values/strings.xml`
- Test: `app/src/test/java/com/example/fit/ui/routines/RoutinesStateTest.kt`, `app/src/androidTest/java/com/example/fit/ui/RoutinesContentTest.kt`

**Interfaces:**
- Consumes: `RoutineRepository` (Task 6); `TodayHolder`, `overloadViewModel`, `SectionLabel`, `RoutineEditorRoute` (Task 11).
- Produces:

```kotlin
data class WeekDayRow(val day: DayOfWeek, val routineId: Long?, val routineName: String?, val isToday: Boolean)
data class RoutineRow(val id: Long, val name: String, val exerciseCount: Int)
data class RoutinesUiState(val week: List<WeekDayRow> = emptyList(), val routines: List<RoutineRow> = emptyList())
fun buildRoutinesState(routines: List<Routine>, schedule: Map<DayOfWeek, Long?>, today: LocalDate): RoutinesUiState   // pure

interface RoutinesActions {
    fun setDay(day: DayOfWeek, routineId: Long?); fun create(name: String)
    fun edit(id: Long); fun duplicate(id: Long); fun delete(id: Long)
}
class RoutinesViewModel(routines: RoutineRepository, today: TodayHolder) : ViewModel()
@Composable fun RoutinesScreen(onEdit: (Long) -> Unit)
@Composable fun RoutinesContent(state: RoutinesUiState, actions: RoutinesActions, modifier: Modifier = Modifier)
```

Behaviour (spec 5.2, 6.2, mockup 02 screen 2):
- "My week": seven rows Monday to Sunday with the short day name ("Mon") and the routine name or "Rest day". Today's row is highlighted. A tap opens a chooser listing every routine and "Rest day".
- "My routines": each routine with "5 exercises" (plural-aware) and a menu described "More options for Chest": "Edit", "Duplicate", "Delete". Delete asks "Delete Chest? Past workouts stay in your history." with "Delete" and "Cancel".
- "New routine" asks for a name (text field, "Create"), creates it and opens the editor. A blank name cannot be created.

- [ ] **Step 1: Write the failing tests**

```kotlin
// RoutinesStateTest.kt (JVM)
class RoutinesStateTest {
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private val chest = Routine(1, "Chest", listOf(RoutineExercise(bench, List(3) { SetValues(200, 10) })))
    private val empty = Routine(2, "Empty", emptyList())
    @Test fun buildsSevenDaysAndTheRoutineList() {
        val state = buildRoutinesState(listOf(chest, empty), mapOf(DayOfWeek.MONDAY to 1L, DayOfWeek.THURSDAY to 99L), LocalDate.of(2026, 11, 12))
        assertEquals(DayOfWeek.entries.toList(), state.week.map { it.day })
        assertEquals(WeekDayRow(DayOfWeek.MONDAY, 1, "Chest", isToday = false), state.week[0])
        assertEquals(WeekDayRow(DayOfWeek.THURSDAY, null, null, isToday = true), state.week[3])   // unknown routine id reads as rest
        assertEquals(listOf(RoutineRow(1, "Chest", 1), RoutineRow(2, "Empty", 0)), state.routines)
    }
}

// RoutinesContentTest.kt
class RoutinesContentTest {
    @get:Rule val rule = createComposeRule()
    private class Recorder : RoutinesActions {
        val calls = mutableListOf<String>()
        override fun setDay(day: DayOfWeek, routineId: Long?) { calls += "setDay $day $routineId" }
        override fun create(name: String) { calls += "create $name" }
        override fun edit(id: Long) { calls += "edit $id" }
        override fun duplicate(id: Long) { calls += "duplicate $id" }
        override fun delete(id: Long) { calls += "delete $id" }
    }
    private val actions = Recorder()
    private val state = RoutinesUiState(
        week = DayOfWeek.entries.map { WeekDayRow(it, if (it == DayOfWeek.MONDAY) 1 else null, if (it == DayOfWeek.MONDAY) "Chest" else null, it == DayOfWeek.THURSDAY) },
        routines = listOf(RoutineRow(1, "Chest", 5), RoutineRow(2, "Legs", 1)))
    private fun show() = rule.setContent { OverloadTheme { RoutinesContent(state, actions) } }

    @Test fun showsTheWeekAndTheRoutines() {
        show()
        rule.onNodeWithText("Mon").assertIsDisplayed(); rule.onAllNodesWithText("Rest day").assertCountEquals(6)
        rule.onNodeWithText("5 exercises").assertIsDisplayed(); rule.onNodeWithText("1 exercise").assertIsDisplayed()
    }
    @Test fun aDayCanBeSetToARoutineOrRest() {
        show()
        rule.onNodeWithText("Tue").performClick(); rule.onNode(hasText("Legs") and hasAnyAncestor(isDialog())).performClick()
        rule.onNodeWithText("Mon").performClick(); rule.onNode(hasText("Rest day") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("setDay TUESDAY 2", "setDay MONDAY null"), actions.calls)
    }
    @Test fun menuOffersEditDuplicateDelete_andDeleteAsksFirst() {
        show()
        rule.onNodeWithContentDescription("More options for Chest").performClick(); rule.onNodeWithText("Duplicate").performClick()
        rule.onNodeWithContentDescription("More options for Chest").performClick(); rule.onNodeWithText("Delete").performClick()
        rule.onNodeWithText("Delete Chest? Past workouts stay in your history.").assertIsDisplayed()
        assertEquals(listOf("duplicate 1"), actions.calls)
        rule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("duplicate 1", "delete 1"), actions.calls)
    }
    @Test fun newRoutineNeedsAName() {
        show()
        rule.onNodeWithText("New routine").performScrollTo().performClick()
        rule.onNodeWithText("Create").assertIsNotEnabled()
        rule.onNodeWithTag("routine-name-field").performTextInput("Arms"); rule.onNodeWithText("Create").performClick()
        assertEquals(listOf("create Arms"), actions.calls)
    }
}
```

The day chooser is a dialog (so `isDialog()` matches).

- [ ] **Step 2: Run both tests and confirm they fail** (compilation)

- [ ] **Step 3: Implement the state builder, ViewModel, screen and navigation wiring**

- [ ] **Step 4: Run both tests and confirm they pass**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add routines tab with weekly schedule"
```

---

### Task 15: Routine editor and exercise picker

**Files:**
- Create: `app/src/main/java/com/example/fit/ui/components/SetRow.kt`, `ui/routines/{RoutineEditorViewModel,RoutineEditorScreen}.kt`, `ui/picker/{ExercisePickerViewModel,ExercisePickerScreen}.kt`
- Modify: `domain/Format.kt` (add `setsSummary`), `ui/nav/AppNav.kt` (replace the editor and picker placeholders), `res/values/strings.xml`
- Test: `app/src/test/java/com/example/fit/ui/routines/EditorLogicTest.kt`, `app/src/androidTest/java/com/example/fit/ui/{RoutineEditorContentTest,ExercisePickerContentTest}.kt`

**Interfaces:**
- Consumes: `RoutineRepository`, `ExerciseRepository`, `WorkoutRepository.observeWorkout` (Tasks 5 to 7); `filterExercises` (Task 2); `NumberStepper`, `ExerciseImage`, `KindTag`, routes, `PICKED_EXERCISE_KEY` (Task 11).
- Produces:

```kotlin
// domain/Format.kt
/** "3 sets · 40 kg × 10" (first set; body weight with extra reads "+5 kg × 10"), "3 sets · 10 reps" for
 *  REPS_ONLY and for BODY_WEIGHT without extra, "10 min" for CARDIO. */
fun setsSummary(kind: ExerciseKind, sets: List<SetValues>): String

// ui/components/SetRow.kt  (used by the editor, Today and history)
@Composable fun SetRow(
    number: Int, kind: ExerciseKind, values: SetValues, onStep: (SetField, Int) -> Unit, modifier: Modifier = Modifier,
    done: Boolean? = null, onToggleDone: (() -> Unit)? = null,   // tick shown only when both are given
    isRecord: Boolean = false,                                   // "New record" badge
    onRemove: (() -> Unit)? = null,                              // small remove button when given
)

// ui/routines
/** Index a dragged row lands on. */
fun dropIndex(startIndex: Int, dragOffsetPx: Float, rowHeightPx: Float, count: Int): Int
data class RoutineEditorUiState(val routineId: Long, val name: String = "", val exercises: List<RoutineExercise> = emptyList())
interface RoutineEditorActions {
    fun rename(name: String); fun step(exerciseId: String, position: Int, field: SetField, steps: Int)
    fun addSet(exerciseId: String); fun removeSet(exerciseId: String, position: Int)
    fun removeExercise(exerciseId: String); fun move(fromIndex: Int, toIndex: Int)
    fun addExercise(); fun done()
}
class RoutineEditorViewModel(routineId: Long, routines: RoutineRepository) : ViewModel()
@Composable fun RoutineEditorScreen(routineId: Long, pickedExerciseId: String?, onPickExercise: () -> Unit, onDone: () -> Unit)
@Composable fun RoutineEditorContent(state: RoutineEditorUiState, actions: RoutineEditorActions, modifier: Modifier = Modifier)

// ui/picker
data class PickerUiState(val query: String = "", val group: MuscleGroup? = null,
                         val results: List<Exercise> = emptyList(), val alreadyAdded: Set<String> = emptySet()) {
    val offerCreate: Boolean   // query not blank and no result is named exactly that, ignoring case
}
interface PickerActions {
    fun setQuery(query: String); fun setGroup(group: MuscleGroup?); fun pick(exerciseId: String)
    fun createCustom(name: String, group: MuscleGroup, kind: ExerciseKind)
    fun renameCustom(id: String, name: String); fun deleteCustom(id: String); fun back()
}
class ExercisePickerViewModel(route: ExercisePickerRoute, exercises: ExerciseRepository, routines: RoutineRepository, workouts: WorkoutRepository) : ViewModel()
@Composable fun ExercisePickerScreen(route: ExercisePickerRoute, onPicked: (String) -> Unit, onBack: () -> Unit)
@Composable fun ExercisePickerContent(state: PickerUiState, actions: PickerActions, modifier: Modifier = Modifier)
```

`SetRow` content descriptions, with the set number: "Decrease weight, set 1", "Increase weight, set 1", "Decrease reps, set 1", "Increase reps, set 1", "Decrease minutes", "Increase minutes", "Set 1 done" (a toggle), "Remove set 1". WEIGHTS and BODY_WEIGHT rows show weight (`formatStepWeight`, unit "kg") and reps; REPS_ONLY rows show reps only; CARDIO rows show minutes only (unit "min").

Editor (spec 6.2, mockup 02 screen 3): a top bar "Edit routine" with "Done"; the name field (test tag `routine-name-field`) saves as it changes; each exercise row shows a drag handle, its picture, name, `KindTag` and `setsSummary`, and a button "Remove Bench Press". Tapping a row opens it in place to show a `SetRow` per set (with remove buttons, hidden when one set is left) and "Add set" (not for cardio). Holding the drag handle and dragging moves the row; rows have a fixed height so `dropIndex` decides where it lands. "Add exercise" opens the picker with this routine's id; the id the picker returns is added through the repository.

Picker (spec 6.2, mockup 02 screen 4): a search field (test tag `exercise-search-field`); chips "All", "Chest", "Back", "Shoulders", "Arms", "Legs", "Core", "Cardio"; rows with picture, name, "Chest · barbell" and `KindTag`. Exercises in `alreadyAdded` show "Added" and cannot be picked. When `offerCreate`, a last row "Create "press" as my own exercise" opens a dialog with the name, a muscle group choice and a kind choice ("Weights", "Body weight", "Reps only", "Cardio"), and "Create"; the new exercise is picked at once. Both choices are rows of chips inside the dialog, not dropdown menus; they start on the picker's current group (Chest when "All") and on "Weights". Holding a custom exercise offers "Rename" and "Delete". The ViewModel fills `alreadyAdded` from the routine or workout named in the route.

Navigation: `pick` stores the id under `PICKED_EXERCISE_KEY` in the previous back stack entry's `SavedStateHandle` and pops. The receiving screen reads it once and clears it.

- [ ] **Step 1: Write the failing tests**

```kotlin
// EditorLogicTest.kt (JVM)
class EditorLogicTest {
    @Test fun dropIndexFollowsTheDrag() {
        assertEquals(2, dropIndex(0, 130f, 64f, 5)); assertEquals(1, dropIndex(1, 20f, 64f, 5))
        assertEquals(0, dropIndex(3, -1000f, 64f, 5)); assertEquals(4, dropIndex(3, 1000f, 64f, 5))
    }
    @Test fun summaries() {
        assertEquals("3 sets · 40 kg × 10", setsSummary(ExerciseKind.WEIGHTS, List(3) { SetValues(400, 10) }))
        assertEquals("1 set · 42.5 kg × 8", setsSummary(ExerciseKind.WEIGHTS, listOf(SetValues(425, 8))))
        assertEquals("3 sets · 10 reps", setsSummary(ExerciseKind.BODY_WEIGHT, List(3) { SetValues(0, 10) }))
        assertEquals("3 sets · +5 kg × 10", setsSummary(ExerciseKind.BODY_WEIGHT, List(3) { SetValues(50, 10) }))
        assertEquals("3 sets · 20 reps", setsSummary(ExerciseKind.REPS_ONLY, List(3) { SetValues(0, 20) }))
        assertEquals("10 min", setsSummary(ExerciseKind.CARDIO, listOf(SetValues(minutes = 10))))
    }
    @Test fun pickerOffersCreateOnlyForANewName() {
        val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
        assertFalse(PickerUiState(query = "", results = listOf(bench)).offerCreate)
        assertFalse(PickerUiState(query = "bench press", results = listOf(bench)).offerCreate)
        assertTrue(PickerUiState(query = "press", results = listOf(bench)).offerCreate)
    }
}

// RoutineEditorContentTest.kt
class RoutineEditorContentTest {
    @get:Rule val rule = createComposeRule()
    private class Recorder : RoutineEditorActions {
        val calls = mutableListOf<String>()
        override fun rename(name: String) { calls += "rename $name" }
        override fun step(exerciseId: String, position: Int, field: SetField, steps: Int) { calls += "step $exerciseId $position $field $steps" }
        override fun addSet(exerciseId: String) { calls += "addSet $exerciseId" }
        override fun removeSet(exerciseId: String, position: Int) { calls += "removeSet $exerciseId $position" }
        override fun removeExercise(exerciseId: String) { calls += "removeExercise $exerciseId" }
        override fun move(fromIndex: Int, toIndex: Int) { calls += "move $fromIndex $toIndex" }
        override fun addExercise() { calls += "addExercise" }
        override fun done() { calls += "done" }
    }
    private val actions = Recorder()
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private val run = Exercise("treadmill", "Treadmill", MuscleGroup.CARDIO, "Cardio", "machine", ExerciseKind.CARDIO)
    private val state = RoutineEditorUiState(1, "Chest", listOf(
        RoutineExercise(bench, List(3) { SetValues(400, 10) }), RoutineExercise(run, listOf(SetValues(minutes = 10)))))

    @Test fun showsNameExercisesAndSummaries() {
        rule.setContent { OverloadTheme { RoutineEditorContent(state, actions) } }
        rule.onNodeWithTag("routine-name-field").assertTextContains("Chest")
        rule.onNodeWithText("3 sets · 40 kg × 10").assertIsDisplayed(); rule.onNodeWithText("10 min").assertIsDisplayed()
        rule.onNodeWithText("cardio").assertIsDisplayed()
    }
    @Test fun openingAnExerciseEditsItsStartingSets() {
        rule.setContent { OverloadTheme { RoutineEditorContent(state, actions) } }
        rule.onNodeWithText("Bench Press").performClick()
        rule.onNodeWithContentDescription("Increase weight, set 2").performClick()
        rule.onNodeWithContentDescription("Decrease reps, set 1").performClick()
        rule.onNodeWithContentDescription("Remove set 3").performClick(); rule.onNodeWithText("Add set").performClick()
        assertEquals(listOf("step bench-press 1 WEIGHT 1", "step bench-press 0 REPS -1", "removeSet bench-press 2", "addSet bench-press"), actions.calls)
    }
    @Test fun exercisesCanBeRemovedAndAdded_andDoneLeaves() {
        rule.setContent { OverloadTheme { RoutineEditorContent(state, actions) } }
        rule.onNodeWithContentDescription("Remove Treadmill").performClick()
        rule.onNodeWithText("Add exercise").performScrollTo().performClick(); rule.onNodeWithText("Done").performClick()
        assertEquals(listOf("removeExercise treadmill", "addExercise", "done"), actions.calls)
    }
}

// ExercisePickerContentTest.kt
class ExercisePickerContentTest {
    @get:Rule val rule = createComposeRule()
    private class Recorder : PickerActions {
        val calls = mutableListOf<String>()
        override fun setQuery(query: String) { calls += "query $query" }
        override fun setGroup(group: MuscleGroup?) { calls += "group $group" }
        override fun pick(exerciseId: String) { calls += "pick $exerciseId" }
        override fun createCustom(name: String, group: MuscleGroup, kind: ExerciseKind) { calls += "create $name $group $kind" }
        override fun renameCustom(id: String, name: String) { calls += "rename $id $name" }
        override fun deleteCustom(id: String) { calls += "delete $id" }
        override fun back() { calls += "back" }
    }
    private val actions = Recorder()
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private val pushUp = Exercise("push-up", "Push-up", MuscleGroup.CHEST, "Chest", "body only", ExerciseKind.BODY_WEIGHT)
    private fun show(state: PickerUiState) = rule.setContent { OverloadTheme { ExercisePickerContent(state, actions) } }

    @Test fun listsResultsAndPicksOne() {
        show(PickerUiState(results = listOf(bench, pushUp), alreadyAdded = setOf("push-up")))
        rule.onNodeWithText("Chest · barbell").assertIsDisplayed(); rule.onNodeWithText("body weight").assertIsDisplayed()
        rule.onNodeWithText("Added").assertIsDisplayed()
        rule.onNodeWithText("Push-up").performClick(); rule.onNodeWithText("Bench Press").performClick()
        assertEquals(listOf("pick bench-press"), actions.calls)
    }
    @Test fun searchAndChipsArePassedOn() {
        show(PickerUiState(results = listOf(bench)))
        rule.onNodeWithTag("exercise-search-field").performTextInput("pre"); rule.onNodeWithText("Legs").performClick()
        rule.onNodeWithText("All").performClick()
        assertEquals(listOf("query pre", "group LEGS", "group null"), actions.calls)
    }
    @Test fun aMissingExerciseCanBeCreated() {
        show(PickerUiState(query = "Y Raise", results = emptyList()))
        rule.onNodeWithText("Create \"Y Raise\" as my own exercise").performClick()
        rule.onNode(hasText("Shoulders") and hasAnyAncestor(isDialog())).performClick()
        rule.onNode(hasText("Reps only") and hasAnyAncestor(isDialog())).performClick()
        rule.onNode(hasText("Create") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("create Y Raise SHOULDERS REPS_ONLY"), actions.calls)
    }
}
```

- [ ] **Step 2: Run the three tests and confirm they fail** (compilation)

- [ ] **Step 3: Implement `SetRow`, the editor, the picker and their navigation wiring**

- [ ] **Step 4: Run the three tests and confirm they pass**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add routine editor and exercise picker"
```

---

### Task 16: Today screen

**Files:**
- Create: `app/src/main/java/com/example/fit/domain/TodayState.kt`, `ui/today/{TodayViewModel,TodayScreen}.kt`
- Modify: `ui/nav/AppNav.kt` (replace the `screen-today` placeholder), `res/values/strings.xml`
- Test: `app/src/test/java/com/example/fit/domain/TodayStateTest.kt`, `app/src/androidTest/java/com/example/fit/ui/{TodayContentTest,TodayViewModelTest}.kt`

**Interfaces:**
- Consumes: `WorkoutRepository` (Tasks 7, 8), `RoutineRepository`, `ProfileRepository`; `weekCount` (Task 2); `sessionsOf`, `exerciseRecords`, `recordSetPositions`, `workoutRecords` (Task 3); `SetRow` (Task 15); `ExerciseImage`, routes (Task 11).
- Produces:

```kotlin
// domain/TodayState.kt
data class TodaySetUi(val position: Int, val values: SetValues, val done: Boolean, val isRecord: Boolean)
data class TodayExerciseUi(
    val exercise: Exercise, val sets: List<TodaySetUi>, val doneCount: Int,
    val loadHg: Long, val totalReps: Int, val minutes: Int,   // over ticked sets
    val bestWeightHg: Int?,                                   // max weight from saved history; null if none or not a weights kind
)
data class FinishSummary(val loadHg: Long, val setsDone: Int, val cardioMinutes: Int, val records: List<RecordBroken>)

sealed interface TodayState {
    val date: LocalDate
    val week: WeekCount
    data class RestDay(override val date: LocalDate, override val week: WeekCount, val routines: List<Routine>) : TodayState
    data class Active(
        override val date: LocalDate, override val week: WeekCount, val routineName: String,
        val isPreview: Boolean,                // true while no workout row exists yet
        val exercises: List<TodayExerciseUi>, val openExerciseId: String?,
        val totalLoadHg: Long, val cardioMinutes: Int, val tickedCount: Int, val untickedCount: Int,
    ) : TodayState { val canChangeRoutine: Boolean get() = tickedCount == 0; val canFinish: Boolean get() = tickedCount > 0 }
    data class Finished(
        override val date: LocalDate, override val week: WeekCount, val routineName: String, val summary: FinishSummary,
        val recordLines: List<Pair<String, RecordType>>,   // exercise name and record type, one per summary.records entry
    ) : TodayState
}
data class TodayInput(
    val date: LocalDate, val workout: Workout?, val scheduledRoutine: Routine?, val routines: List<Routine>,
    val saved: List<Workout>, val scheduledDays: Set<DayOfWeek>, val openExerciseId: String?,
)
fun buildTodayState(input: TodayInput): TodayState
/** The exercise to show open after a tick: the current one while it still has an unticked set, otherwise the first
 *  exercise in order that has one, otherwise null. */
fun nextOpenExercise(exercises: List<WorkoutExercise>, openExerciseId: String?): String?

// ui/today
interface TodayActions {
    fun open(exerciseId: String)
    fun step(exerciseId: String, position: Int, field: SetField, steps: Int)
    fun toggleDone(exerciseId: String, position: Int)
    fun addSet(exerciseId: String); fun removeLastSet(exerciseId: String)
    fun chooseRoutine()                       // "Change routine" and, on a rest day, "Start a routine"
    fun addExercise(); fun swapExercise(exerciseId: String); fun removeExercise(exerciseId: String)
    fun finish(); fun continueWorkout(); fun showExercise(exerciseId: String)
}
data class BodyWeightPrompt(val exerciseId: String, val position: Int)
class TodayViewModel(workouts: WorkoutRepository, routines: RoutineRepository, profile: ProfileRepository, today: TodayHolder) : ViewModel() {
    val state: StateFlow<TodayState?>                    // null while loading
    val bodyWeightPrompt: StateFlow<BodyWeightPrompt?>
    fun open(exerciseId: String)
    fun step(exerciseId: String, position: Int, field: SetField, steps: Int)
    fun toggleDone(exerciseId: String, position: Int)
    fun addSet(exerciseId: String); fun removeLastSet(exerciseId: String)
    fun startRoutine(routineId: Long)
    fun addExercise(exerciseId: String, alsoUpdateRoutine: Boolean)
    fun replaceExercise(oldExerciseId: String, newExerciseId: String, alsoUpdateRoutine: Boolean)
    fun removeExercise(exerciseId: String, alsoUpdateRoutine: Boolean)
    fun confirmBodyWeight(weightHg: Int); fun dismissBodyWeightPrompt()
    fun finish(); fun continueWorkout()
}
@Composable fun TodayScreen(pickedExerciseId: String?, onPickExercise: (workoutId: Long?) -> Unit, onShowExercise: (String) -> Unit)
@Composable fun TodayContent(state: TodayState, actions: TodayActions, modifier: Modifier = Modifier)
@Composable fun FinishWarningDialog(untickedCount: Int, onFinish: () -> Unit, onGoBack: () -> Unit)
@Composable fun ScopeChoiceDialog(onOnlyToday: () -> Unit, onAlsoRoutine: () -> Unit, onDismiss: () -> Unit)
```

`buildTodayState` (spec 5.3, 5.6, 5.9):
- No workout and no scheduled routine → `RestDay`. No workout but a scheduled routine → `Active` with `isPreview = true`, built from the routine's starting sets, nothing ticked. A FINISHED workout → `Finished`. An IN_PROGRESS workout → `Active`.
- `week` = `weekCount(saved.map { it.date }, scheduledDays, date)`.
- `isRecord` comes from `recordSetPositions` against saved sessions dated before `date`; `bestWeightHg` from `exerciseRecords` of the same sessions.
- `openExerciseId`: the input's value if that exercise is in the list, otherwise the first exercise with an unticked set.

ViewModel rules:
- It combines `today.date`, `observeWorkoutOn(date)`, `observeRoutines()`, `observeSchedule()` and `observeSaved()` into `TodayInput`, and holds the open exercise id.
- Every changing action runs one at a time, in order. If the state is a preview, the action first calls `ensureWorkout(date, scheduledRoutineId)` and then acts on that workout (spec 5.6: stored at once).
- After `toggleDone` ticks a set, the open exercise becomes `nextOpenExercise(...)`.
- Ticking a BODY_WEIGHT set while the workout has no body weight does not tick; it sets `bodyWeightPrompt` instead (spec 5.4). The screen shows a dialog with a decimal field checked with `parseKgToHg`; `confirmBodyWeight` stores today's body weight, ticks that set and clears the prompt.
- `startRoutine` calls `startWorkout(date, routineId)`; the screen only offers it while `canChangeRoutine`.
- `finish` calls `workouts.finish`; `continueWorkout` calls `reopen`.
- `FinishSummary` and `recordLines` in `buildTodayState` come from `workoutRecords(saved, workout)`, with each exercise's name taken from the workout.

Screen (spec 6.1, mockup 02 screen 1):
- Header: `formatDayLong(date)`, the routine name, "Change routine" (disabled once a set is ticked), "This week: 3 of 5 done" with one dot per planned day.
- One exercise open at a time. A closed row shows a status circle (filled when all sets are ticked), the name, `KindTag`, and "2 of 3 sets" with its load, reps or minutes. The open card shows `ExerciseImage` (tap → `showExercise`), the name, "Best 45 kg" when known, a menu "More options for Bench Press" with "Remove last set" (disabled at one set), "Swap exercise", "Remove exercise", then a `SetRow` per set with the tick, then "Add set" (not for cardio) and "Load 740 kg" (or "45 reps", "25 min").
- "Add exercise" at the end of the list.
- Bottom bar: "Total load today" with `formatKg` (followed by "· 25 min cardio" when there are ticked cardio minutes), and "Finish workout", enabled only when `canFinish`.
- Finish with unticked sets shows `FinishWarningDialog`: "4 sets are not ticked and will not be saved. Finish anyway?" ("1 set is not ticked and will not be saved. Finish anyway?" for one), buttons "Finish" and "Go back".
- Add, swap and remove show `ScopeChoiceDialog`: "Change only today's workout, or the routine as well?" with "Only today" and "Also update the routine". Add and swap go through the picker first (opened with the workout id, or none during a preview).
- Rest day: "Rest day", the week line, and "Start a routine".
- Finished: "Workout finished", tiles "Total load", "Sets done", "Cardio" (only if any minutes), a line per record broken such as "New record: Bench Press, max weight", and "Continue workout".

- [ ] **Step 1: Write the failing tests**

```kotlin
// TodayStateTest.kt (JVM)
class TodayStateTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private val run = Exercise("treadmill", "Treadmill", MuscleGroup.CARDIO, "Cardio", "machine", ExerciseKind.CARDIO)
    private val chest = Routine(1, "Chest", listOf(
        RoutineExercise(bench, listOf(SetValues(400, 10), SetValues(425, 8), SetValues(450, 6))), RoutineExercise(run, listOf(SetValues(minutes = 10)))))
    private val days = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
    private fun sets(vararg done: Boolean, hg: List<Int> = listOf(400, 425, 450), reps: List<Int> = listOf(10, 8, 6)) =
        done.mapIndexed { i, d -> WorkoutSet(i, SetValues(hg[i], reps[i]), d) }
    private fun workout(status: WorkoutStatus, benchSets: List<WorkoutSet>, date: LocalDate = monday, id: Long = 7) = Workout(
        id, date, 1, "Chest", status, null, listOf(WorkoutExercise(bench, benchSets), WorkoutExercise(run, listOf(WorkoutSet(0, SetValues(minutes = 10), false)))))
    private val lastWeek = workout(WorkoutStatus.FINISHED, sets(true, true, hg = listOf(400, 425), reps = listOf(10, 8)), monday.minusDays(7), id = 3)
    private fun input(workout: Workout?, scheduled: Routine? = chest, saved: List<Workout> = listOf(lastWeek), open: String? = null) =
        TodayInput(monday, workout, scheduled, listOf(chest), saved, days, open)

    @Test fun restDayWhenNothingIsScheduled() {
        val state = buildTodayState(input(null, scheduled = null)) as TodayState.RestDay
        assertEquals(WeekCount(0, 5), state.week); assertEquals(listOf(chest), state.routines)
    }
    @Test fun scheduledRoutineIsShownAsAPreview() {
        val state = buildTodayState(input(null)) as TodayState.Active
        assertTrue(state.isPreview); assertEquals("Chest", state.routineName); assertEquals("bench-press", state.openExerciseId)
        assertEquals(listOf(400, 425, 450), state.exercises[0].sets.map { it.values.weightHg })
        assertEquals(0, state.tickedCount); assertEquals(4, state.untickedCount)
        assertTrue(state.canChangeRoutine); assertFalse(state.canFinish); assertEquals(425, state.exercises[0].bestWeightHg)
    }
    @Test fun activeWorkoutCountsLoadTicksAndRecords() {
        val state = buildTodayState(input(workout(WorkoutStatus.IN_PROGRESS, sets(true, true, true)))) as TodayState.Active
        assertFalse(state.isPreview); assertEquals(10100L, state.totalLoadHg); assertEquals(3, state.tickedCount); assertEquals(1, state.untickedCount)
        assertFalse(state.canChangeRoutine); assertTrue(state.canFinish)
        assertEquals(listOf(false, false, true), state.exercises[0].sets.map { it.isRecord })   // 45 kg beats last week's 42.5
        assertEquals(3, state.exercises[0].doneCount); assertEquals("treadmill", state.openExerciseId)
    }
    @Test fun firstEverSessionShowsNoRecordBadge() {
        val state = buildTodayState(input(workout(WorkoutStatus.IN_PROGRESS, sets(true, true, true)), saved = emptyList())) as TodayState.Active
        assertTrue(state.exercises[0].sets.none { it.isRecord }); assertNull(state.exercises[0].bestWeightHg)
    }
    @Test fun finishedWorkoutShowsItsSummaryAndCountsForTheWeek() {
        val done = workout(WorkoutStatus.FINISHED, sets(true, true, true))
        val state = buildTodayState(input(done, saved = listOf(lastWeek, done))) as TodayState.Finished
        assertEquals(FinishSummary(10100L, 3, 0, listOf(RecordBroken("bench-press", RecordType.MAX_WEIGHT), RecordBroken("bench-press", RecordType.BEST_SESSION))),
            state.summary.copy(records = state.summary.records.sortedBy { it.type }))
        assertEquals(listOf("Bench Press" to RecordType.MAX_WEIGHT, "Bench Press" to RecordType.BEST_SESSION),
            state.recordLines.sortedBy { it.second })
        assertEquals(WeekCount(1, 5), state.week)
    }
    @Test fun requestedOpenExerciseIsKeptIfItExists() {
        assertEquals("treadmill", (buildTodayState(input(null, open = "treadmill")) as TodayState.Active).openExerciseId)
        assertEquals("bench-press", (buildTodayState(input(null, open = "gone")) as TodayState.Active).openExerciseId)
    }
    @Test fun openExerciseMovesOnWhenItsLastSetIsTicked() {
        val exercises = workout(WorkoutStatus.IN_PROGRESS, sets(true, true, false)).exercises
        assertEquals("bench-press", nextOpenExercise(exercises, "bench-press"))
        val finishedBench = workout(WorkoutStatus.IN_PROGRESS, sets(true, true, true)).exercises
        assertEquals("treadmill", nextOpenExercise(finishedBench, "bench-press"))
        val allDone = finishedBench.map { it.copy(sets = it.sets.map { s -> s.copy(done = true) }) }
        assertNull(nextOpenExercise(allDone, "treadmill"))
    }
}

// TodayContentTest.kt
class TodayContentTest {
    @get:Rule val rule = createComposeRule()
    private class Recorder : TodayActions {
        val calls = mutableListOf<String>()
        override fun open(exerciseId: String) { calls += "open $exerciseId" }
        override fun step(exerciseId: String, position: Int, field: SetField, steps: Int) { calls += "step $exerciseId $position $field $steps" }
        override fun toggleDone(exerciseId: String, position: Int) { calls += "toggle $exerciseId $position" }
        override fun addSet(exerciseId: String) { calls += "addSet $exerciseId" }
        override fun removeLastSet(exerciseId: String) { calls += "removeLastSet $exerciseId" }
        override fun chooseRoutine() { calls += "chooseRoutine" }
        override fun addExercise() { calls += "addExercise" }
        override fun swapExercise(exerciseId: String) { calls += "swap $exerciseId" }
        override fun removeExercise(exerciseId: String) { calls += "remove $exerciseId" }
        override fun finish() { calls += "finish" }
        override fun continueWorkout() { calls += "continue" }
        override fun showExercise(exerciseId: String) { calls += "show $exerciseId" }
    }
    private val actions = Recorder()
    private val monday = LocalDate.of(2026, 11, 9)
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS, imageBase = "exercises/bench-press")
    private val run = Exercise("treadmill", "Treadmill", MuscleGroup.CARDIO, "Cardio", "machine", ExerciseKind.CARDIO, imageBase = "exercises/treadmill")
    private val active = TodayState.Active(monday, WeekCount(3, 5), "Chest + Triceps", isPreview = false,
        exercises = listOf(
            TodayExerciseUi(bench, listOf(TodaySetUi(0, SetValues(400, 10), true, false), TodaySetUi(1, SetValues(425, 8), true, true),
                TodaySetUi(2, SetValues(450, 6), false, false)), doneCount = 2, loadHg = 7400, totalReps = 18, minutes = 0, bestWeightHg = 450),
            TodayExerciseUi(run, listOf(TodaySetUi(0, SetValues(minutes = 10), false, false)), 0, 0, 0, 0, null)),
        openExerciseId = "bench-press", totalLoadHg = 7400, cardioMinutes = 0, tickedCount = 2, untickedCount = 2)
    private fun show(state: TodayState) = rule.setContent { OverloadTheme { TodayContent(state, actions) } }

    @Test fun showsHeaderOpenExerciseAndTotals() {
        show(active)
        rule.onNodeWithText("Monday, 9 November").assertIsDisplayed(); rule.onNodeWithText("Chest + Triceps").assertIsDisplayed()
        rule.onNodeWithText("This week: 3 of 5 done").assertIsDisplayed(); rule.onNodeWithText("Best 45 kg").assertIsDisplayed()
        rule.onNodeWithText("42.5").assertIsDisplayed(); rule.onNodeWithText("New record").assertIsDisplayed()
        rule.onNodeWithText("Load 740 kg").assertIsDisplayed(); rule.onNodeWithText("740 kg").assertIsDisplayed()
        rule.onNodeWithText("0 of 1 sets").assertIsDisplayed(); rule.onNodeWithText("Change routine").assertIsNotEnabled()
    }
    @Test fun setControlsReportWhatWasTouched() {
        show(active)
        rule.onNodeWithContentDescription("Increase weight, set 3").performClick()
        rule.onNodeWithContentDescription("Set 3 done").performClick()
        rule.onNodeWithText("Add set").performClick(); rule.onNodeWithText("Treadmill").performClick()
        rule.onNodeWithTag("exercise-image").performClick()
        assertEquals(listOf("step bench-press 2 WEIGHT 1", "toggle bench-press 2", "addSet bench-press", "open treadmill", "show bench-press"), actions.calls)
    }
    @Test fun exerciseMenuAndFinish() {
        show(active)
        rule.onNodeWithContentDescription("More options for Bench Press").performClick(); rule.onNodeWithText("Swap exercise").performClick()
        rule.onNodeWithContentDescription("More options for Bench Press").performClick(); rule.onNodeWithText("Remove last set").performClick()
        rule.onNodeWithText("Add exercise").performScrollTo().performClick(); rule.onNodeWithText("Finish workout").performClick()
        assertEquals(listOf("swap bench-press", "removeLastSet bench-press", "addExercise", "finish"), actions.calls)
    }
    @Test fun finishIsOffUntilSomethingIsTicked() {
        show(active.copy(tickedCount = 0, untickedCount = 4, isPreview = true))
        rule.onNodeWithText("Finish workout").assertIsNotEnabled(); rule.onNodeWithText("Change routine").assertIsEnabled()
    }
    @Test fun restDayOffersToStartARoutine() {
        show(TodayState.RestDay(monday, WeekCount(0, 5), emptyList()))
        rule.onNodeWithText("Rest day").assertIsDisplayed(); rule.onNodeWithText("Start a routine").performClick()
        assertEquals(listOf("chooseRoutine"), actions.calls)
    }
    @Test fun finishedShowsSummaryAndContinue() {
        show(TodayState.Finished(monday, WeekCount(4, 5), "Chest + Triceps",
            FinishSummary(10100, 3, 0, listOf(RecordBroken("bench-press", RecordType.MAX_WEIGHT))),
            recordLines = listOf("Bench Press" to RecordType.MAX_WEIGHT)))
        rule.onNodeWithText("Workout finished").assertIsDisplayed(); rule.onNodeWithText("1,010 kg").assertIsDisplayed()
        rule.onNodeWithText("New record: Bench Press, max weight").assertIsDisplayed()
        rule.onNodeWithText("Continue workout").performClick(); assertEquals(listOf("continue"), actions.calls)
    }
    @Test fun finishWarningCountsTheUntickedSets() {
        var finished = false
        rule.setContent { OverloadTheme { FinishWarningDialog(4, onFinish = { finished = true }, onGoBack = {}) } }
        rule.onNodeWithText("4 sets are not ticked and will not be saved. Finish anyway?").assertIsDisplayed()
        rule.onNodeWithText("Finish").performClick(); assertTrue(finished)
    }
    @Test fun finishWarningForOneSet() {
        rule.setContent { OverloadTheme { FinishWarningDialog(1, onFinish = {}, onGoBack = {}) } }
        rule.onNodeWithText("1 set is not ticked and will not be saved. Finish anyway?").assertIsDisplayed()
        rule.onNodeWithText("Go back").assertIsDisplayed()
    }
    @Test fun scopeChoiceOffersTodayOrRoutine() {
        var scope = ""
        rule.setContent { OverloadTheme { ScopeChoiceDialog(onOnlyToday = { scope = "today" }, onAlsoRoutine = { scope = "routine" }, onDismiss = {}) } }
        rule.onNodeWithText("Change only today's workout, or the routine as well?").assertIsDisplayed()
        rule.onNodeWithText("Only today").assertIsDisplayed()
        rule.onNodeWithText("Also update the routine").performClick(); assertEquals("routine", scope)
    }
}

// TodayViewModelTest.kt (instrumented; real repositories on an in-memory database, no UI)
@RunWith(AndroidJUnit4::class)
class TodayViewModelTest {
    private val clock = MutableClock(LocalDateTime.of(2026, 11, 9, 10, 0))   // a Monday
    private lateinit var c: AppContainer
    private lateinit var vm: TodayViewModel
    private suspend fun active(match: (TodayState.Active) -> Boolean = { true }) =
        withTimeout(5_000) { vm.state.filterIsInstance<TodayState.Active>().first(match) }
    private fun TodayState.Active.sets(id: String) = exercises.first { it.exercise.id == id }.sets
    private suspend fun stored() = c.workouts.observeWorkoutOn(c.today.date.value).first()

    @Before fun setUp() = runBlocking {
        c = AppContainer(ApplicationProvider.getApplicationContext(), inMemory = true, clock = clock)
        seedTestExercises(c.db)
        val id = c.routines.createRoutine("Mixed"); listOf("bench-press", "pull-up").forEach { c.routines.addExercise(id, it) }
        c.routines.setScheduleDay(DayOfWeek.MONDAY, id)
        c.onForeground()
        vm = TodayViewModel(c.workouts, c.routines, c.profile, c.today)
    }
    @After fun tearDown() = c.db.close()

    @Test fun firstTouchTurnsThePreviewIntoAStoredWorkout() = runBlocking {
        assertTrue(active().isPreview); assertNull(stored())
        vm.step("bench-press", 0, SetField.WEIGHT, +1)
        assertEquals(205, active { !it.isPreview }.sets("bench-press")[0].values.weightHg)
        assertEquals(205, stored()!!.exercises[0].sets[0].values.weightHg)
    }
    @Test fun rapidStepsAllLand() = runBlocking {
        repeat(30) { vm.step("bench-press", 0, SetField.WEIGHT, +1) }
        active { it.sets("bench-press")[0].values.weightHg == 350 }   // times out if a step was lost
        Unit
    }
    @Test fun tickingTheLastSetOpensTheNextExercise() = runBlocking {
        assertEquals("bench-press", active().openExerciseId)
        for (i in 0..2) vm.toggleDone("bench-press", i)
        assertEquals("pull-up", active { it.tickedCount == 3 }.openExerciseId)
    }
    @Test fun bodyWeightSetWaitsForABodyWeight() = runBlocking {
        vm.toggleDone("pull-up", 0)
        assertEquals(BodyWeightPrompt("pull-up", 0), withTimeout(5_000) { vm.bodyWeightPrompt.filterNotNull().first() })
        assertEquals(0, active().tickedCount)
        vm.confirmBodyWeight(700)
        val state = active { it.tickedCount == 1 }
        assertTrue(state.sets("pull-up")[0].done); assertEquals(7000L, state.totalLoadHg)
        assertEquals(700, c.profile.currentBodyWeightHg()); assertNull(vm.bodyWeightPrompt.value)
    }
    @Test fun finishThenContinue() = runBlocking {
        vm.toggleDone("bench-press", 0); active { it.tickedCount == 1 }
        vm.finish()
        assertEquals(2000L, withTimeout(5_000) { vm.state.filterIsInstance<TodayState.Finished>().first() }.summary.loadHg)
        vm.continueWorkout(); assertEquals(1, active { !it.isPreview }.tickedCount)
    }
    @Test fun routineCanBeSwitchedBeforeTheFirstTick() = runBlocking {
        val other = c.routines.createRoutine("Core").also { c.routines.addExercise(it, "crunches") }
        vm.startRoutine(other)
        assertEquals(listOf("crunches"), active { it.routineName == "Core" }.exercises.map { it.exercise.id })
    }
}
```

Record lines read "New record: Bench Press, max weight" / "best set" / "best session" / "most reps in a set" / "most reps in a session".

- [ ] **Step 2: Run both tests and confirm they fail** (compilation)

- [ ] **Step 3: Implement `TodayState.kt`, the ViewModel, the screen, the dialogs and the navigation wiring**

- [ ] **Step 4: Run both tests and confirm they pass**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add Today screen"
```

---

### Task 17: Exercise detail and Progress

**Files:**
- Create: `app/src/main/java/com/example/fit/ui/exercise/{ExerciseDetailViewModel,ExerciseDetailScreen}.kt`, `ui/progress/{ProgressViewModel,ProgressScreen}.kt`
- Modify: `ui/nav/AppNav.kt` (replace the `screen-progress` and exercise detail placeholders), `res/values/strings.xml`
- Test: `app/src/test/java/com/example/fit/ui/progress/ProgressStateTest.kt`, `app/src/androidTest/java/com/example/fit/ui/ProgressContentTest.kt`

**Interfaces:**
- Consumes: `observeSaved` (Task 8), `RoutineRepository`, `ExerciseRepository`, `ProfileRepository`; Task 3 functions; `filterExercises` (Task 2); charts (Task 12); `StatTile`, `ExerciseImage`, `KindTag` (Task 11).
- Produces:

```kotlin
// ui/exercise
data class RoutineFilter(val routineId: Long, val name: String)
data class ExerciseDetailUiState(
    val exercise: Exercise, val records: ExerciseRecords,
    val filters: List<RoutineFilter>,          // existing routines that have at least one session of this exercise, in routine order
    val selectedRoutineId: Long?, val series: SessionSeries, val n: Int,
)
fun buildExerciseDetailState(exercise: Exercise, saved: List<Workout>, routines: List<Routine>, selectedRoutineId: Long?, n: Int): ExerciseDetailUiState
@Composable fun ExerciseDetailScreen(exerciseId: String, onBack: () -> Unit)
@Composable fun ExerciseDetailContent(state: ExerciseDetailUiState, onSelectRoutine: (Long?) -> Unit, onChangeN: (Int) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier)

// ui/progress
enum class ProgressSection { EXERCISES, ROUTINES, WEEKLY, BODY }
data class ExerciseProgressRow(val exercise: Exercise, val headline: String)    // "Max 45 kg", "Most 25 reps in a set", "Longest 35 min"
data class RoutineProgress(val routineId: Long, val name: String, val series: SessionSeries,
                           val best: ChartPoint?, val last: ChartPoint?, val exercises: List<ExerciseProgressRow>)
data class WeeklyProgress(val weeks: List<WeekTotal>, val thisWeek: WeekTotal?, val bestWeek: WeekTotal?)
/** Exercises that have at least one saved session, sorted by name. */
fun exerciseProgressRows(saved: List<Workout>): List<ExerciseProgressRow>
fun routineProgress(routine: Routine, saved: List<Workout>, n: Int): RoutineProgress   // uses the saved workouts whose routineId is this routine's
fun weeklyProgress(saved: List<Workout>, today: LocalDate): WeeklyProgress            // bestWeek: highest load; null when every week is zero
fun weekColumns(weeks: List<WeekTotal>, cardio: Boolean): List<ColumnDatum>
data class ProgressUiState(
    val section: ProgressSection = ProgressSection.EXERCISES, val query: String = "",
    val exerciseRows: List<ExerciseProgressRow> = emptyList(),
    val routineOptions: List<RoutineFilter> = emptyList(), val routine: RoutineProgress? = null,
    val weekly: WeeklyProgress = WeeklyProgress(emptyList(), null, null),
    val bodyWeights: List<BodyWeightEntry> = emptyList(), val n: Int = DEFAULT_AVERAGE_N,
)
interface ProgressActions {
    fun setSection(section: ProgressSection); fun setQuery(query: String); fun selectRoutine(routineId: Long)
    fun changeN(n: Int); fun showExercise(exerciseId: String)
}
@Composable fun ProgressScreen(onShowExercise: (String) -> Unit)
@Composable fun ProgressContent(state: ProgressUiState, actions: ProgressActions, modifier: Modifier = Modifier)
```

Behaviour (spec 5.9, 5.10, 6.3, 6.4, mockup 03 screens 1 to 3):
- **Exercise detail:** back arrow and name; a large `ExerciseImage`; "Chest · barbell"; record tiles by kind: "Max weight", "Best set" (note "42.5 × 8"), "Best session" for weights kinds; "Most reps in a set", "Most reps in a session" for reps only; "Longest session" for cardio; each with `formatDayShort` of its date, or "—" when there is no record. Filter chips "All routines" plus one per `filters` entry, shown only when there are two or more routines. Then `SessionChart` and `AverageNControl`.
- **Progress:** a four-part switch "Exercises", "Routines", "Weekly", "Body".
  - Exercises: a search field (test tag `progress-search-field`) and the rows; a tap opens the detail.
  - Routines: a dropdown of all routines (the ViewModel selects the first routine that has history, else the first); tiles "Best session" and "Last session"; `SessionChart`; `AverageNControl`; "Exercises in this routine" rows that open the detail.
  - Weekly: tiles "This week so far" (load, note "3 workouts") and "Best week" (load, note "2 – 8 Nov"); `ColumnChart` "Total load per week (kg)" in `chartLoad`; `ColumnChart` "Cardio per week (minutes)" in `chartCardio`. `weekColumns` labels each column with its week start (`formatDayShort`), marks the last (current) week `muted` with note "so far", and gives tooltips "Week of 2 Nov · 1,410 kg" or "Week of 2 Nov · 45 min" ("This week so far · …" for the current week).
  - Body: `TrendChart` "Body weight (kg)".
- With no saved workouts, the Exercises and Routines sections show "No workouts yet. Finish a workout to see your progress here."
- Changing N stores it through `ProfileRepository.setAverageN`, so it is the same everywhere.

- [ ] **Step 1: Write the failing tests**

```kotlin
// ProgressStateTest.kt (JVM)
class ProgressStateTest {
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private val crunch = Exercise("crunches", "Crunches", MuscleGroup.CORE, "Abdominals", "body only", ExerciseKind.REPS_ONLY)
    private val run = Exercise("treadmill", "Treadmill", MuscleGroup.CARDIO, "Cardio", "machine", ExerciseKind.CARDIO)
    private fun ex(e: Exercise, vararg v: SetValues) = WorkoutExercise(e, v.mapIndexed { i, s -> WorkoutSet(i, s, true) })
    private fun workout(id: Long, day: Int, routineId: Long?, vararg ex: WorkoutExercise) =
        Workout(id, d(day), routineId, "R$routineId", WorkoutStatus.FINISHED, null, ex.toList())
    private val chest = Routine(1, "Chest", listOf(RoutineExercise(bench, emptyList()), RoutineExercise(run, emptyList())))
    private val full = Routine(2, "Full Body", listOf(RoutineExercise(bench, emptyList())))
    private val unused = Routine(3, "Unused", emptyList())
    private val saved = listOf(
        workout(1, 2, 1, ex(bench, SetValues(400, 10), SetValues(425, 8)), ex(run, SetValues(minutes = 20))),   // 740 kg
        workout(2, 5, 2, ex(bench, SetValues(450, 6)), ex(crunch, SetValues(0, 25))),                             // 270 kg
        workout(3, 9, 1, ex(bench, SetValues(400, 10), SetValues(425, 8), SetValues(450, 6))))                    // 1010 kg

    @Test fun exerciseRowsListOnlyExercisesWithHistory() = assertEquals(
        listOf("Bench Press" to "Max 45 kg", "Crunches" to "Most 25 reps in a set", "Treadmill" to "Longest 20 min"),
        exerciseProgressRows(saved).map { it.exercise.name to it.headline })
    @Test fun emptyHistory_givesEmptyProgress() {
        assertEquals(emptyList<ExerciseProgressRow>(), exerciseProgressRows(emptyList()))
        assertEquals(WeeklyProgress(emptyList(), null, null), weeklyProgress(emptyList(), d(12)))
        val none = routineProgress(chest, emptyList(), 10)
        assertNull(none.best); assertNull(none.last); assertTrue(none.series.points.isEmpty())
    }
    @Test fun routineProgressUsesOnlyThatRoutinesWorkouts() {
        val progress = routineProgress(chest, saved, 10)
        assertEquals(listOf(740.0, 1010.0), progress.series.points.map { it.value })
        assertEquals(ChartPoint(d(9), 1010.0), progress.best); assertEquals(ChartPoint(d(9), 1010.0), progress.last)
        assertEquals(listOf("Bench Press", "Treadmill"), progress.exercises.map { it.exercise.name })
    }
    @Test fun exerciseDetailOffersFiltersForRoutinesWithSessions() {
        val state = buildExerciseDetailState(bench, saved, listOf(chest, full, unused), selectedRoutineId = null, n = 10)
        assertEquals(listOf(RoutineFilter(1, "Chest"), RoutineFilter(2, "Full Body")), state.filters)
        assertEquals(listOf(740.0, 270.0, 1010.0), state.series.points.map { it.value })
        assertEquals(450, state.records.maxWeightHg?.value)
        val filtered = buildExerciseDetailState(bench, saved, listOf(chest, full, unused), selectedRoutineId = 2, n = 10)
        assertEquals(listOf(270.0), filtered.series.points.map { it.value })
        assertEquals(450, filtered.records.maxWeightHg?.value)   // records ignore the filter
    }
    @Test fun weeklyProgressAndColumns() {
        val weekly = weeklyProgress(saved, d(12))
        assertEquals(listOf(d(2), d(9)), weekly.weeks.map { it.weekStart })
        assertEquals(WeekTotal(d(9), 10100, 0, 1), weekly.thisWeek); assertEquals(WeekTotal(d(2), 10100, 20, 2), weekly.bestWeek)
        val load = weekColumns(weekly.weeks, cardio = false)
        assertEquals(ColumnDatum("2 Nov", 1010.0, "Week of 2 Nov · 1,010 kg"), load[0])
        assertEquals(ColumnDatum("9 Nov", 1010.0, "This week so far · 1,010 kg", muted = true, note = "so far"), load[1])
        assertEquals(ColumnDatum("2 Nov", 20.0, "Week of 2 Nov · 20 min"), weekColumns(weekly.weeks, cardio = true)[0])
    }
}
```

(In `weeklyProgressAndColumns` both weeks total 1,010 kg; `bestWeek` is the earlier one, because a later equal week is not a new best.)

```kotlin
// ProgressContentTest.kt
class ProgressContentTest {
    @get:Rule val rule = createComposeRule()
    private class Recorder : ProgressActions {
        val calls = mutableListOf<String>()
        override fun setSection(section: ProgressSection) { calls += "section $section" }
        override fun setQuery(query: String) { calls += "query $query" }
        override fun selectRoutine(routineId: Long) { calls += "routine $routineId" }
        override fun changeN(n: Int) { calls += "n $n" }
        override fun showExercise(exerciseId: String) { calls += "show $exerciseId" }
    }
    private val actions = Recorder()
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS, imageBase = "exercises/bench-press")
    private val series = SessionSeries(Measure.LOAD_KG, listOf(ChartPoint(d(2), 740.0), ChartPoint(d(9), 1010.0)), listOf(740.0, 875.0))
    private val rows = listOf(ExerciseProgressRow(bench, "Max 45 kg"))

    @Test fun emptyHistory_showsAHint() {
        rule.setContent { OverloadTheme { ProgressContent(ProgressUiState(), actions) } }
        rule.onNodeWithText("No workouts yet. Finish a workout to see your progress here.").assertIsDisplayed()
    }
    @Test fun exercisesSectionListsRowsAndOpensOne() {
        rule.setContent { OverloadTheme { ProgressContent(ProgressUiState(exerciseRows = rows), actions) } }
        rule.onNodeWithText("Max 45 kg").assertIsDisplayed(); rule.onNodeWithText("Bench Press").performClick()
        rule.onNodeWithText("Weekly").performClick()
        assertEquals(listOf("show bench-press", "section WEEKLY"), actions.calls)
    }
    @Test fun routinesSectionShowsTilesAndChart() {
        val state = ProgressUiState(section = ProgressSection.ROUTINES, exerciseRows = rows, routineOptions = listOf(RoutineFilter(1, "Chest")),
            routine = RoutineProgress(1, "Chest", series, best = ChartPoint(d(9), 1010.0), last = ChartPoint(d(9), 1010.0), exercises = rows))
        rule.setContent { OverloadTheme { ProgressContent(state, actions) } }
        rule.onNodeWithText("Best session").assertIsDisplayed(); rule.onNodeWithText("Last session").assertIsDisplayed()
        rule.onAllNodesWithText("1,010 kg").assertCountEquals(2); rule.onNodeWithText("Load per session (kg)").assertExists()
        rule.onNodeWithContentDescription("More sessions").performScrollTo().performClick()
        assertEquals(listOf("n 11"), actions.calls)
    }
    @Test fun weeklySectionShowsBothCharts() {
        val weeks = listOf(WeekTotal(d(2), 14100, 45, 4), WeekTotal(d(9), 5000, 20, 1))
        val state = ProgressUiState(section = ProgressSection.WEEKLY, weekly = WeeklyProgress(weeks, weeks[1], weeks[0]))
        rule.setContent { OverloadTheme { ProgressContent(state, actions) } }
        rule.onNodeWithText("This week so far").assertIsDisplayed(); rule.onNodeWithText("500 kg").assertIsDisplayed()
        rule.onNodeWithText("Best week").assertIsDisplayed(); rule.onNodeWithText("2 – 8 Nov").assertIsDisplayed()
        rule.onNodeWithText("Total load per week (kg)").assertExists(); rule.onNodeWithText("Cardio per week (minutes)").assertExists()
    }
    @Test fun exerciseDetailShowsRecordsFiltersAndChart() {
        val state = ExerciseDetailUiState(bench, ExerciseRecords(Dated(450, d(5)), BestSet(4000, 400, 10, d(2)), Dated(10100L, d(5))),
            listOf(RoutineFilter(1, "Chest"), RoutineFilter(2, "Full Body")), selectedRoutineId = null, series = series, n = 10)
        val picked = mutableListOf<Long?>()
        rule.setContent { OverloadTheme { ExerciseDetailContent(state, onSelectRoutine = { picked += it }, onChangeN = {}, onBack = {}) } }
        rule.onNodeWithText("Max weight").assertIsDisplayed(); rule.onNodeWithText("45 kg").assertIsDisplayed()
        rule.onNodeWithText("Best set").assertIsDisplayed(); rule.onNodeWithText("400 kg").assertIsDisplayed(); rule.onNodeWithText("40 × 10").assertIsDisplayed()
        rule.onNodeWithText("Best session").assertIsDisplayed(); rule.onNodeWithText("1,010 kg").assertIsDisplayed()
        rule.onNodeWithText("Full Body").performClick(); rule.onNodeWithText("All routines").performClick()
        assertEquals(listOf<Long?>(2, null), picked)
    }
}
```

- [ ] **Step 2: Run both tests and confirm they fail** (compilation)

- [ ] **Step 3: Implement the builders, both ViewModels, both screens and the navigation wiring**

- [ ] **Step 4: Run both tests and confirm they pass**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add exercise detail and Progress screens"
```

---

### Task 18: History and editing a saved workout

**Files:**
- Create: `app/src/main/java/com/example/fit/ui/history/{HistoryViewModel,HistoryScreen,WorkoutDetailViewModel,WorkoutDetailScreen}.kt`
- Modify: `domain/Load.kt` (add `tickedOnly`), `ui/nav/AppNav.kt` (replace the `screen-history` and workout detail placeholders), `res/values/strings.xml`
- Test: `app/src/test/java/com/example/fit/ui/history/HistoryStateTest.kt`, `app/src/androidTest/java/com/example/fit/ui/HistoryContentTest.kt`

**Interfaces:**
- Consumes: `observeSaved`, `observeWorkout`, `adjustSet`, `addSet`, `removeSet`, `deleteWorkout` (Tasks 7, 8); `recordCounts` (Task 3); `SetRow` (Task 15).
- Produces:

```kotlin
// domain/Load.kt
/** The same workout holding ticked sets only; exercises left without sets are dropped. */
fun Workout.tickedOnly(): Workout

// ui/history
data class HistoryRow(val workoutId: Long, val date: LocalDate, val routineName: String,
                      val exerciseCount: Int, val setCount: Int, val metric: String, val records: Int)
data class HistoryMonth(val month: YearMonth, val rows: List<HistoryRow>)
/** Newest month first, newest day first. metric: load ("6,240 kg") when above zero, else cardio minutes ("35 min"), else reps ("120 reps"). */
fun buildHistory(saved: List<Workout>): List<HistoryMonth>
@Composable fun HistoryScreen(onOpen: (Long) -> Unit)
@Composable fun HistoryContent(months: List<HistoryMonth>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier)

interface WorkoutDetailActions {
    fun step(exerciseId: String, position: Int, field: SetField, steps: Int)
    fun removeSet(exerciseId: String, position: Int); fun addSet(exerciseId: String)
    fun deleteWorkout(); fun back()
}
class WorkoutDetailViewModel(workoutId: Long, workouts: WorkoutRepository) : ViewModel()
@Composable fun WorkoutDetailScreen(workoutId: Long, onBack: () -> Unit)
@Composable fun WorkoutDetailContent(workout: Workout, actions: WorkoutDetailActions, modifier: Modifier = Modifier)   // workout is already tickedOnly()
```

Behaviour (spec 5.11, mockup 03 screen 4):
- **History:** month headings ("November 2026"); each row shows the day number and short weekday ("11", "Wed"), the routine name, "6 exercises · 18 sets" (plural-aware: "1 exercise · 2 sets"), the metric, and a badge "1 record" / "2 records" when above zero. A tap opens the workout. With nothing saved: "No workouts yet".
- **Workout detail:** `formatDayLong(date)` and the routine name; total load; per exercise its name and a `SetRow` per ticked set with a remove button and no tick, numbered 1, 2, 3 in order but addressed by each set's own `position`; "Add set" per exercise, which calls `addSet(..., done = true)`; a "Delete workout" action that asks "Delete this workout? This cannot be undone." with "Delete" and "Cancel".
- Removing the last ticked set of the workout deletes the workout and goes back.
- Edits never touch the routine's starting values (spec 5.7).

- [ ] **Step 1: Write the failing tests**

```kotlin
// HistoryStateTest.kt (JVM)
class HistoryStateTest {
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)
    private val run = Exercise("treadmill", "Treadmill", MuscleGroup.CARDIO, "Cardio", "machine", ExerciseKind.CARDIO)
    private val crunch = Exercise("crunches", "Crunches", MuscleGroup.CORE, "Abdominals", "body only", ExerciseKind.REPS_ONLY)
    private fun ex(e: Exercise, vararg sets: Pair<SetValues, Boolean>) = WorkoutExercise(e, sets.mapIndexed { i, (v, done) -> WorkoutSet(i, v, done) })
    private fun workout(id: Long, date: LocalDate, name: String, vararg ex: WorkoutExercise) =
        Workout(id, date, 1, name, WorkoutStatus.FINISHED, null, ex.toList())
    private val saved = listOf(
        workout(1, LocalDate.of(2026, 10, 30), "Chest", ex(bench, SetValues(400, 10) to true)),
        workout(2, LocalDate.of(2026, 11, 2), "Chest", ex(bench, SetValues(400, 10) to true, SetValues(450, 6) to true)),
        workout(3, LocalDate.of(2026, 11, 7), "Cardio", ex(run, SetValues(minutes = 35) to true)),
        workout(4, LocalDate.of(2026, 11, 9), "Core", ex(crunch, SetValues(0, 60) to true, SetValues(0, 60) to true)))

    @Test fun groupsByMonthNewestFirst_withTheRightMetric() {
        val months = buildHistory(saved)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), months.map { it.month })
        assertEquals(listOf(4L, 3L, 2L), months[0].rows.map { it.workoutId })
        assertEquals(listOf("120 reps", "35 min", "670 kg"), months[0].rows.map { it.metric })
        // against 30 Oct: max weight 45 > 40 and session 670 > 400 kg are records; best set ties at 400 kg, so it is not
        assertEquals(HistoryRow(2, LocalDate.of(2026, 11, 2), "Chest", exerciseCount = 1, setCount = 2, metric = "670 kg", records = 2), months[0].rows[2])
        assertEquals(0, months[1].rows[0].records)
    }
    @Test fun emptyHistory_isAnEmptyList() = assertEquals(emptyList<HistoryMonth>(), buildHistory(emptyList()))
    @Test fun tickedOnlyDropsUntickedSetsAndEmptyExercises() {
        val mixed = workout(9, LocalDate.of(2026, 11, 9), "Chest",
            ex(bench, SetValues(400, 10) to true, SetValues(425, 8) to false, SetValues(450, 6) to true), ex(run, SetValues(minutes = 10) to false))
        val ticked = mixed.tickedOnly()
        assertEquals(listOf("bench-press"), ticked.exercises.map { it.exercise.id })
        assertEquals(listOf(0, 2), ticked.exercises[0].sets.map { it.position })
    }
}
```

```kotlin
// HistoryContentTest.kt
class HistoryContentTest {
    @get:Rule val rule = createComposeRule()
    private class Recorder : WorkoutDetailActions {
        val calls = mutableListOf<String>()
        override fun step(exerciseId: String, position: Int, field: SetField, steps: Int) { calls += "step $exerciseId $position $field $steps" }
        override fun removeSet(exerciseId: String, position: Int) { calls += "removeSet $exerciseId $position" }
        override fun addSet(exerciseId: String) { calls += "addSet $exerciseId" }
        override fun deleteWorkout() { calls += "delete" }
        override fun back() { calls += "back" }
    }
    private val actions = Recorder()
    private val wednesday = LocalDate.of(2026, 11, 11)
    private val bench = Exercise("bench-press", "Bench Press", MuscleGroup.CHEST, "Chest", "barbell", ExerciseKind.WEIGHTS)

    @Test fun listShowsRowsAndOpensOne() {
        val opened = mutableListOf<Long>()
        val months = listOf(HistoryMonth(YearMonth.of(2026, 11), listOf(
            HistoryRow(5, wednesday, "Leg + Shoulder", 6, 18, "6,240 kg", records = 0),
            HistoryRow(4, wednesday.minusDays(1), "Back + Biceps", 5, 15, "5,480 kg", records = 1))))
        rule.setContent { OverloadTheme { HistoryContent(months, onOpen = { opened += it }) } }
        rule.onNodeWithText("November 2026").assertIsDisplayed(); rule.onNodeWithText("6 exercises · 18 sets").assertIsDisplayed()
        rule.onNodeWithText("6,240 kg").assertIsDisplayed(); rule.onNodeWithText("1 record").assertIsDisplayed()
        rule.onNodeWithText("Wed").assertIsDisplayed(); rule.onNodeWithText("Back + Biceps").performClick()
        assertEquals(listOf(4L), opened)
    }
    @Test fun emptyHistory_showsAMessage() {
        rule.setContent { OverloadTheme { HistoryContent(emptyList(), onOpen = {}) } }
        rule.onNodeWithText("No workouts yet").assertIsDisplayed()
    }
    @Test fun detailEditsSetsByTheirOwnPosition_andDeleteAsksFirst() {
        val workout = Workout(5, wednesday, 1, "Chest", WorkoutStatus.FINISHED, null,
            listOf(WorkoutExercise(bench, listOf(WorkoutSet(0, SetValues(400, 10), true), WorkoutSet(2, SetValues(450, 6), true)))))
        rule.setContent { OverloadTheme { WorkoutDetailContent(workout, actions) } }
        rule.onNodeWithText("Wednesday, 11 November").assertIsDisplayed(); rule.onNodeWithText("670 kg").assertIsDisplayed()
        rule.onNodeWithContentDescription("Increase weight, set 2").performClick()     // second row shown, stored position 2
        rule.onNodeWithContentDescription("Remove set 1").performClick(); rule.onNodeWithText("Add set").performClick()
        rule.onNodeWithText("Delete workout").performScrollTo().performClick()
        rule.onNodeWithText("Delete this workout? This cannot be undone.").assertIsDisplayed()
        rule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("step bench-press 2 WEIGHT 1", "removeSet bench-press 0", "addSet bench-press", "delete"), actions.calls)
    }
}
```

- [ ] **Step 2: Run both tests and confirm they fail** (compilation)

- [ ] **Step 3: Implement `tickedOnly`, the history builder, both ViewModels, both screens and the navigation wiring**

- [ ] **Step 4: Run both tests and confirm they pass**

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Add history list and saved workout editing"
```

---

### Task 19: The whole loop, the release build and a look at the real app

**Files:**
- Test: `app/src/androidTest/java/com/example/fit/ui/CoreLoopTest.kt`
- Modify: whatever the checks below turn up

**Interfaces:**
- Consumes: `launchApp`, `MutableClock` (Task 11) and every screen.
- Produces: nothing new.

- [ ] **Step 1: Write the end-to-end test**

```kotlin
@RunWith(AndroidJUnit4::class)
class CoreLoopTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val clock = MutableClock(LocalDateTime.of(2026, 11, 9, 10, 0))   // a Monday: starter schedule says Chest + Triceps

    @Test fun trainFinishSeeHistory_thenNextWeekStartsFromWhatWasDone() {
        launchApp(clock).use { scenario ->
            compose.onNodeWithText("Skip").performClick()
            compose.onNodeWithText("Chest + Triceps").assertIsDisplayed(); compose.onNodeWithText("Monday, 9 November").assertIsDisplayed()

            compose.onNodeWithContentDescription("Increase weight, set 1").performClick()          // 20.0 -> 20.5 kg
            compose.onNodeWithContentDescription("Set 1 done").performClick()
            compose.onNodeWithContentDescription("Set 2 done").performClick()
            compose.onNodeWithText("Finish workout").performClick()
            compose.onNodeWithText("13 sets are not ticked and will not be saved. Finish anyway?").assertIsDisplayed()
            compose.onNodeWithText("Finish").performClick()
            compose.onNodeWithText("Workout finished").assertIsDisplayed(); compose.onNodeWithText("405 kg").assertIsDisplayed()

            compose.onNodeWithTag("tab-history").performClick()
            compose.onNodeWithText("Chest + Triceps").assertIsDisplayed(); compose.onNodeWithText("405 kg").assertIsDisplayed()
            compose.onNodeWithText("1 exercise · 2 sets").assertIsDisplayed()
            compose.onNodeWithTag("tab-progress").performClick(); compose.onNodeWithText("Bench Press").assertIsDisplayed()

            clock.now = LocalDateTime.of(2026, 11, 16, 10, 0)                                      // the next Monday
            scenario.moveToState(Lifecycle.State.STARTED); scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("tab-today").performClick()
            compose.onNodeWithText("Monday, 16 November").assertIsDisplayed(); compose.onNodeWithText("This week: 0 of 5 done").assertIsDisplayed()
            compose.onNodeWithText("20.5").assertIsDisplayed()                                     // set 1 carried forward
            compose.onAllNodesWithText("20.0").assertCountEquals(2)                                // sets 2 and 3
        }
    }
}
```

- [ ] **Step 2: Run it.** It should pass if Tasks 1 to 18 are right. If it fails, find the cause in the task that owns the behaviour and fix it there, with a test at that level.

- [ ] **Step 3: Run everything**

```bash
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Check the spec's hard limits on the built app**

```bash
./gradlew :app:assembleDebug :app:assembleRelease --console=plain          # release runs R8; it must succeed
grep -c "android.permission" app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml   # expect 0
grep -rnE 'Text\(\s*"[A-Za-z]' app/src/main/java | grep -v "@Preview" ; echo "hard-coded strings above (expect none)"
ls -la app/build/outputs/apk/debug/app-debug.apk                              # expect under 30 MB
```

The merged manifest does contain `app.overload.tracker.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. AndroidX adds that app-private entry itself; it is not a user permission and is expected. If the merged manifest path differs under this Gradle version, find it with `find app/build -name AndroidManifest.xml -path "*merged*"`.

- [ ] **Step 5: Look at the real app on the emulator, in light and dark**

Install the debug build (`./gradlew :app:installDebug`), start it, and walk through: welcome → Today (tick, hold + on a weight, finish, continue) → Routines (change a day, duplicate, edit, add an exercise through search, create a custom exercise) → Progress (all four sections, change N, tap a chart point) → an exercise detail → History (open, edit, delete) → Profile (body weight, export a backup, import it again). Switch dark mode with `adb shell cmd uimode night yes` and repeat the main screens. Take screenshots with `adb exec-out screencap -p > shot.png` into a folder outside the repository and compare them with the three mockup PNGs. Fix clipped or overflowing text, unreadable contrast, touch targets that are too small, and anything that differs from the spec.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "Add end-to-end test and fix issues found in the final pass"
```

