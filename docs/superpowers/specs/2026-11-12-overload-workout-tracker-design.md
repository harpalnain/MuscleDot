# Overload: offline workout planner and tracker (design)

Date: 2026-11-12
Status: waiting for the owner's approval

Items marked **(owner)** were decided by the app's owner in conversation.
Items marked **(my call)** were decided by the designer and can be overruled.
Anything not marked is a direct consequence of an owner decision.

---

## 1. What this is and why

An Android app for planning and logging gym workouts. It works with no
internet and no account.

The point of the app is **progressive overload**: lifting a little more over
time. Everything in the design serves three moments:

1. **At the gym:** open the app, see today's routine with the numbers from
   last time already filled in, raise a weight or a rep, tick each set.
2. **After the gym:** only the sets that were ticked become history.
3. **Later:** see whether the load is going up, per exercise, per routine and
   per week. On the exercise and routine charts a moving-average line smooths
   out good and bad days.

The owner uses it personally and plans to publish it on the Play Store later
**(owner)**.

## 2. Goals, non-goals, anti-goals

### Goals

- Works completely offline. No login. **(owner)**
- The user designs any number of routines and puts them on a weekly schedule.
  **(owner)**
- Logging a set at the gym is fast: one tap to tick, one tap to nudge a number.
- Starting numbers come from the last time the routine was done. **(owner)**
- Progress charts for exercises, routines, weeks, cardio and body weight. The
  exercise and routine charts, which are per session, carry a moving average
  over the last N sessions, N chosen by the user. **(owner)**
- Personal records per exercise. **(owner)**
- Data can be moved to a new phone with a backup file. **(owner)**

### Non-goals for this version

Not built now. Do not add them.

- Accounts, cloud sync, sharing, anything that needs a network.
- Reminders or any notification. Rest timer. "Last time" hints beside sets.
  Notes on exercises. Workout duration. **(owner declined each of these)**
- Written how-to instructions for exercises. **(owner declined)**
- Pounds, centimetres, or any unit setting. Weight is always kg, height is
  always feet and inches. **(owner)**
- Adding a workout for a past date. **(owner)**
- More than one routine in a day. **(owner)**
- Photos for custom exercises. **(owner)**
- Typing workout numbers. They change with − and + only. **(owner)**
- A calendar view of history, one-rep-max estimates, supersets, warm-up set
  types, tablet-specific layouts, languages other than English.
- The Play Store release work itself (signing, store listing, privacy policy).
  The app should stay publishable, but publishing is a later project.

### Anti-goals (these make the app a failure even if it "works")

- **Anything that needs the internet.** The app must not request the INTERNET
  permission or any other permission.
- **Losing a tick.** Every tick and every number change is stored at once. If
  the app is closed, killed, or the phone restarts mid-workout, nothing that
  was ticked is lost.
- **Saving what was not done.** A set that was not ticked never reaches
  history, charts or records.
- **Slow logging.** No extra screens, dialogs or confirmations between the
  user and ticking a set.
- **Misleading charts.** One scale per chart. Never two different measures on
  one chart with two axes.

## 3. Starting point

The folder already holds a fresh Android Studio project created from the
"Empty Activity (Compose)" template:

- Kotlin 2.2.10, Android Gradle Plugin 9.5.1, Compose BOM 2026.02.01,
  Material 3
- `minSdk 24`, `compileSdk`/`targetSdk 37`
- namespace and applicationId `com.example.fit`
- one `MainActivity` that shows "Hello Android"
- not yet a git repository

Build on this project. Do not start a new one.

## 4. Words used in this document

| Word | Meaning |
|---|---|
| **Exercise** | One movement, such as Bench Press. Built-in or custom. Has a *kind*: **weights**, **body weight**, **reps only** or **cardio**. |
| **App day** | The app's day runs from 4:00 am to 3:59 am, phone local time **(owner)**. "Today", "the day ends", a workout's date, the weekday used for the schedule and the weeks in section 5.8 all use the app day. |
| **Routine** | A named, ordered list of exercises made by the user, such as "Chest + Triceps". Each exercise in a routine has its own **starting values**. |
| **Starting values** | The sets (weight and reps for each set) a routine will show for an exercise the next time it is done. For cardio: the minutes. |
| **Weekly schedule** | For each weekday, Monday to Sunday, either one routine or "Rest day". |
| **Workout** | One routine done on one calendar day. At most one per day. |
| **Set** | One row in a workout: a weight, a reps count and a done tick. |
| **Load** | weight × reps for one set. Loads are added up over **ticked** sets only. |
| **Saved** | A workout is saved when the user finishes it or when its day ends. Saved workouts are history. |

## 5. Rules

### 5.1 Exercises

- The app ships with about 150 common gym exercises **(owner)**. Section 9
  says where they come from.
- The user can add custom exercises **(owner)**: a name, a muscle group and a
  kind. Custom exercises show a drawn muscle-group icon instead of a photo
  **(owner)**.
- Muscle groups: Chest, Back, Shoulders, Arms, Legs, Core, Cardio **(my
  call)**. A built-in exercise also shows its specific muscle (for example
  Biceps or Triceps) and its equipment as small text.
- Built-in exercises cannot be edited or deleted. A custom exercise can be
  renamed or deleted. Deleting one that has history only hides it from the
  search list; its history, charts and records stay **(my call)**.
- **Cardio exercises** are tracked by minutes only, with a done tick. They
  never count toward load **(owner)**.
- **Reps only exercises** are floor and core movements done without weight,
  such as crunches, sit-ups and leg raises. Each set has reps and a done tick,
  and no weight. They never count toward load **(owner)**. Without this kind,
  60 crunches at 72 kg would add as much load as a whole chest workout.
- **Body weight exercises** are only those where the whole body is lifted:
  the pull-up and chin-up family, the dip family and the push-up family
  **(owner)**. Section 5.4 says how they are counted.
- The four kinds a custom exercise can have are the same: weights, body
  weight, reps only, cardio.
- Timed holds such as the plank are not in the built-in list. A user who
  wants one can create it as a custom cardio exercise and log minutes **(my
  call)**.
- A reps only exercise has no optional weight. A weighted version is a
  separate weights exercise, such as Cable Crunch **(my call)**.
- A routine may mix exercises of every kind **(my call, shown to the owner in
  the playback and not objected to)**.

### 5.2 Routines and the weekly schedule

- Any number of routines **(owner)**.
- A new install starts with five routines, all editable and deletable
  **(owner)**: Full Body, Chest + Triceps, Back + Biceps, Leg + Shoulder,
  Cardio. Section 10 lists their contents.
- A routine can be duplicated **(owner)**. The copy gets the same exercises and
  starting values and the name "<name> copy".
- An exercise appears at most once in a routine **(my call)**.
- The weekly schedule covers all seven days **(owner)**. One routine may sit on
  several days. A day with no routine is a rest day.
- Deleting a routine asks for confirmation. Its saved workouts stay in history
  and keep counting in exercise charts and weekly totals. Days that used it
  become rest days **(my call)**.

### 5.3 Today: which routine

- On a scheduled day the main screen shows that day's routine **(owner)**.
- The user can switch to any other routine for today **(owner)**. Switching
  changes today only, not the schedule **(my call)**.
- Switching is possible only while no set is ticked **(owner)**. To switch
  after ticking, the user unticks everything first.
- On a rest day the screen says "Rest day", shows this week's count, and
  offers "Start a routine" to pick any routine **(owner: any routine on any of
  the 7 days)**.
- One workout per day **(owner)**.

### 5.4 Today: sets and numbers

- Each set has its own weight and reps **(owner)**.
- A new exercise starts with 3 sets **(owner)**. The user can add and remove
  sets. "Add set" copies the values of the last set **(my call)**.
- Weight changes in steps of 0.5 kg, minimum 0 **(owner)**. Reps change in
  steps of 1, minimum 1. Cardio minutes change in steps of 1, minimum 1.
- Numbers change with the − and + buttons only; they cannot be typed
  **(owner)**. Pressing and holding a button repeats and speeds up, so large
  changes stay practical **(my call)**.
- Numbers of a ticked set can still be changed; totals update **(my call)**.
- **Reps only exercises:** a set row has reps − / + and the done tick, and no
  weight control.
- **Body weight exercises** (pull-ups, chin-ups, dips, push-ups): the weight
  field is the *extra* weight and starts at 0. The weight used for load and
  records is body weight from the profile plus the extra weight **(owner)**.
  Full body weight is used for every such exercise, with no per-exercise
  percentage **(my call)**.
- If the profile has no body weight, the app asks for it when the user first
  tries to tick a body weight set, and the set cannot be ticked until it is
  given **(my call)**.
- Each saved workout remembers the body weight that applied on its day, so a
  later change to the profile does not rewrite old loads **(my call)**.

### 5.5 Today: changing exercises during a workout

- The user can add, remove or swap an exercise on the main screen **(owner)**.
- Each time, the app asks: **"Only today"** or **"Also update the routine"**
  **(owner)**.
- Editing a routine in the Routines tab while a workout from it is in progress
  does not change today's workout **(my call)**.

### 5.6 Saving a workout

- The user ticks each set as it is done. Only ticked sets are ever saved
  **(owner)**.
- A workout is saved when the user taps **Finish workout**, or automatically
  when its day ends **(owner)**.
- If some sets are unticked when Finish is tapped, the app warns first, for
  example "4 sets are not ticked and will not be saved. Finish anyway?", with
  Finish and Go back **(owner)**.
- After finishing, the main screen shows a summary (total load, sets done,
  cardio minutes, records broken) and a **Continue workout** button that
  reopens the same workout for the rest of that day **(owner)**.
- A workout with no ticked set at the end of its day leaves nothing in
  history.
- "The day ends" at **4:00 am**, not midnight **(owner)**. A workout started at
  11:30 pm simply continues after midnight and is saved under the day it
  started. The date in the Today header, the weekday used to pick the
  scheduled routine, and "one workout per day" all follow this app day.
- No background job is needed: a workout whose app day is before the current
  one is treated as saved, and the bookkeeping in 5.7 runs the next time the
  app is opened or brought to the front. That bookkeeping must be safe to run
  more than once **(my call)**.
- If the Today screen is left open across 4:00 am, it must notice the change
  at the latest when the app next comes to the front, and move to the new
  day.

### 5.7 What the routine shows next time

- A routine starts from what was done **the last time in that same routine**
  **(owner)**. Two routines that share an exercise keep separate numbers for
  it.
- When a workout is saved, for each exercise in it that has at least one
  ticked set and still belongs to the routine, the routine's starting values
  become, set by set in order:
  - a **ticked** set: the weight and reps that were ticked;
  - an **unticked** set in a position the routine already had: the routine's
    old values for that position **(owner: "still 3 sets")**;
  - an unticked set in a new position (added today, never ticked): dropped
    **(my call)**.
- An exercise with no ticked set leaves the routine unchanged.
- Cardio minutes carry over the same way when ticked. Reps only sets follow
  the same set-by-set rule, with reps alone.
- Exercises added "only today" are not in the routine, so nothing carries.
- Editing a saved workout later in History does **not** change the routine's
  starting values **(my call)**.
- When an exercise is first added to a routine, its starting values are
  copied from its most recent saved workout in any routine. If it has never
  been done: 3 sets of 10 reps (weights: see section 10 for the starting
  weight; body weight: extra 0; reps only: reps alone), or 10 minutes for
  cardio **(my call)**.

### 5.8 Load and totals

- Set load = weight × reps. Body weight exercises use body weight + extra.
- Exercise load in a workout = sum over its ticked sets.
- Workout load = sum over its exercises. Cardio and reps only exercises add
  nothing.
- Weekly load = sum over all saved workouts dated Monday to Sunday of that
  week, all routines together **(owner; Monday start is my call)**. A
  workout's date is its app day.
- Weekly cardio minutes = sum of ticked cardio minutes in that week.
- Weekly workout count on the main screen **(owner)**: "X of Y done", where Y
  is the number of scheduled days in the weekly schedule and X is the number
  of days this week with a saved workout **(the exact counting is my call)**.

### 5.9 Personal records

Per exercise, across all routines **(owner)**:

| Record | Definition |
|---|---|
| **Max weight** | The heaviest weight in any ticked set. |
| **Best set** | The highest weight × reps in one ticked set. |
| **Best session** | The highest total load of the exercise in one workout. |

Per routine **(owner)**: **Best session**, the highest workout load ever for
that routine.

- Each record shows the date it was first reached.
- Records are always worked out from saved history, never stored as separate
  facts, so editing or deleting a workout keeps them correct.
- A "New record" badge appears on a set the moment it is ticked if it beats
  the max weight or best set. Records broken are listed in the finish summary
  and counted on the history row. A badge appears only when there was an
  earlier record to beat, so the very first session of an exercise shows none
  **(my call)**.
- Cardio exercises have no load records. Their detail page shows the longest
  session in minutes **(my call)**.
- Reps only exercises have no load records. Their detail page shows two
  records: most reps in one set, and most reps in one session. The "New
  record" badge works for them on most reps in one set **(my call)**.

### 5.10 Charts

All four chart groups were chosen by the owner.

| Chart | Shows |
|---|---|
| **Exercise** | Load per session for one exercise, with the moving-average line. A filter picks "All routines" or one routine **(owner)**. For a cardio exercise: minutes per session. For a reps only exercise: total reps per session **(owner)**. |
| **Routine** | Workout load each time the routine was done, with the moving-average line and the best session **(owner)**. For a routine whose workouts have no load at all: minutes per session if it has cardio, otherwise total reps per session **(my call)**. |
| **Weekly** | Total load per week as columns, the current week marked "so far". Below it, cardio minutes per week **(owner)**. |
| **Body weight** | Body weight over time, one point per day it was changed **(owner)**. |

**Moving average (owner):** at each session, the mean of that session and the
sessions before it, N sessions in total, within the list being shown (so a
routine filter changes it). Fewer than N sessions: the mean of what exists.
N starts at 10 **(owner)**, can be set from 2 to 20 with − and + beside the
chart, and is one setting shared by all charts and remembered **(sharing it is
my call)**.

Only the Exercise and Routine charts have the moving-average line and the N
control. The Weekly load, weekly cardio and Body weight charts are plain, with
no average and no N control: the owner asked for the average over *sessions*,
and approved these three charts without it in the mockup.

Chart form, as approved in the mockups:

- Each session is a grey dot; the moving average is a blue line with its
  latest value written at the end. Both always appear in a small legend.
- Weekly charts are thin columns, rounded at the top, square at the baseline.
- Tapping a point or column shows its date and exact value.
- Sessions are spaced evenly in order, with dates on the axis.
- One scale per chart. Grid lines are thin and quiet.
- Colours: sessions neutral grey; moving average and load blue (`#2a78d6`
  light, `#3987e5` dark); cardio orange (`#eb6834` light, `#d95926` dark).
  This pair was checked for colour-blind separation and contrast in both
  themes.
- Charts must stay readable with hundreds of sessions. How (scrolling, or the
  most recent sessions with a "show all") is left to the builder.
- A workout still in progress is not in any chart until it is saved **(my
  call)**.

### 5.11 History

- A list of saved workouts, newest first, grouped by month **(list form is my
  call, approved in the mockup)**. Each row: date, routine name, number of
  exercises and ticked sets, total load (minutes for a cardio-only workout),
  and how many records were broken.
- Tapping a row opens the workout: every exercise with its ticked sets.
- Saved workouts can be edited **(owner)**: change a weight, reps or minutes
  with the same − and + controls, delete a set, add a set, or delete the whole
  workout after a confirmation **(the exact edit actions are my call)**.
- Workouts cannot be added for past dates **(owner)**.
- Charts and records follow any edit at once.

### 5.12 Profile

- Name, date of birth, height in feet and inches, body weight in kg
  **(owner)**. All optional.
- The first time the app opens, a short welcome screen asks for these, with a
  Skip button **(owner)**.
- Date of birth shows the age beside it **(my call)**.
- Body weight has a precision of 0.1 kg **(owner)**. Each change is saved with
  its date and feeds the body weight chart.
- Profile fields are a form, so they are typed or picked (text field, date
  picker, feet and inches pickers). Body weight is typed, with − and + beside
  it for 0.1 kg nudges. "Buttons only" in 5.4 is about workout numbers **(my
  call; the owner should say if body weight must also be buttons only)**.

### 5.13 Backup

- **Export** writes one file holding everything the user created: profile,
  body weight history, custom exercises, routines, weekly schedule, all
  workouts, and settings **(owner)**.
- **Import** reads such a file and, after a clear confirmation, replaces all
  data in the app **(replace rather than merge is my call)**.
- The file is chosen through the phone's own save/open file screens, so no
  storage permission is needed.
- The file carries a format version so later app versions can still read it.

## 6. Screens

Approved mockups are in `docs/superpowers/specs/mockups/` (HTML and PNG).
They fix layout and content, not exact colours or spacing.

Five tabs along the bottom **(approved)**: **Today, Routines, Progress,
History, Profile.**

### 6.1 Today (`01-today-layout-options`, option B; `02-routines-flow`, screen 1)

- Header: today's date, routine name, "Change routine", weekly count with
  dots.
- Exercise list in the routine's order. **One exercise open at a time
  (owner chose layout B)**:
  - closed exercise: one line with a status circle, the name, "2 of 3 sets"
    and its load so far (minutes for cardio);
  - open exercise: its picture, name and max weight, then one row per set:
    set number, weight − / +, reps − / +, done tick; "Add set" and the
    exercise's load underneath (a reps only exercise has no weight control and
    shows total reps instead of load; a cardio exercise has one row with
    minutes − / + and a tick);
  - ticking the last set of the open exercise opens the next unfinished one;
  - tapping a closed exercise opens it.
- Tapping the picture opens the exercise detail page (6.4).
- "Add exercise" at the end of the list.
- Fixed at the bottom: total load today and **Finish workout**.
- Other states: rest day (5.3), finished summary (5.6), routine with no
  exercises ("Add exercise").

### 6.2 Routines (`02-routines-flow`, screens 2 to 4)

- **My week:** seven rows, Monday to Sunday, each showing a routine or "Rest
  day"; today is highlighted; tapping a row picks a routine or rest. **(Placing
  the schedule here is my call, approved in the mockup.)**
- **My routines:** every routine with its exercise count and a menu: Edit,
  Duplicate, Delete. "New routine" at the end.
- **Edit routine:** name; exercises with picture, name and a summary of
  starting values; hold and drag to reorder; remove; "Add exercise". Tapping
  an exercise edits its starting sets with the same controls as Today.
- **Add exercise:** search box that filters as the user types; muscle-group
  chips; rows with picture, name, muscle and equipment, and a tag for body
  weight, reps only or cardio; a last row to create the typed name as a custom
  exercise.

### 6.3 Progress (`03-progress-history-profile`, screens 2 and 3)

Four sections switched at the top **(my call, approved in the mockup)**:

- **Exercises:** searchable list of exercises that have history; tapping one
  opens the exercise detail page.
- **Routines:** pick a routine; best session and last session; the routine
  chart; N control; the routine's exercises with their records, each opening
  its detail page.
- **Weekly:** "this week so far" and "best week"; weekly load chart; weekly
  cardio minutes chart.
- **Body:** the body weight chart.

### 6.4 Exercise detail (`03-progress-history-profile`, screen 1)

Reached from the picture on Today or from Progress. Large switching picture;
muscle and equipment; the three records with dates; routine filter; the
exercise chart; N control **(owner chose the detail page; no how-to text)**.

### 6.5 History (`03-…`, screen 4) and 6.6 Profile (`03-…`, screen 5)

As described in 5.11 to 5.13. The body weight chart appears on Profile as well
as under Progress → Body **(my call, approved in the mockup)**.

## 7. Look and feel

- Follows the phone's light or dark setting **(owner)**.
- Material 3, with the app's own fixed colours rather than colours taken from
  the wallpaper, so the charts and the app look the same on every phone **(my
  call)**.
- Calm and legible in a gym: large tap targets on − / + and the tick, strong
  contrast, no decoration competing with the numbers.
- App name **"Overload"** for now; the owner may change it **(owner)**. Keep
  the name in one place.

## 8. How it gets made (designer's calls; the owner handed these over)

- Kotlin, Jetpack Compose, Material 3, one activity, on the existing project.
- All data in a database on the phone (Room). Small settings such as N may
  live there or in DataStore.
- No INTERNET permission and no other permission in the manifest.
- Turn off Android's automatic backup **(owner)**. The template has
  `android:allowBackup="true"`; set it to false and make the backup and data
  extraction rules exclude everything, for cloud backup and for
  phone-to-phone transfer alike **(covering phone-to-phone transfer is my
  call)**. The only way data leaves the phone is the export file the user
  makes.
- Every user-visible text in string resources, English only, so other
  languages can be added later without touching code.
- Set a real `applicationId` now, because `com.example.*` cannot be published.
  Use `app.overload.tracker` as a placeholder; it can still be changed until
  the first Play Store upload. Whether to rename the code namespace is the
  builder's choice.
- The rules in section 5 (load, weekly totals, moving average, records,
  next-time values, day-end saving) should live in plain code that can be
  tested without a phone, and should have unit tests. They are the part of the
  app where a silent mistake does the most damage.
- Version control: a local git repository, set up with the owner's agreement
  **(owner)**. Nothing is uploaded anywhere. `.superpowers/` stays out of it.

### Left to the builder

- Screen-to-screen navigation, state handling and code structure.
- The database layout. It must satisfy: history survives deleting a routine
  or a custom exercise; records are derived, not stored; each workout
  remembers its body weight.
- Drawing charts directly or using a chart library, as long as section 5.10
  holds and nothing needs a network.
- The exact backup file format (JSON is suggested).
- How a set is removed (swipe, long press or a small button), and other small
  interaction details the mockups do not show.
- Upper limits for weight, reps and minutes.
- The app icon (a simple original icon is enough).

## 9. Exercise list and pictures

**Source:** `free-exercise-db` (github.com/yuhonas/free-exercise-db). 876
exercises as JSON with fields including `name`, `category`, `equipment`,
`primaryMuscles` and `images`. Most have two JPG photos, start and end
position, 40 to 75 KB each. A few have none.

**Licence and a caution the owner has accepted:** the repository is released
under the Unlicense (public domain). It does not document who took the
photos, and their origin could not be confirmed. Real animated GIF sets found
online are copyrighted and were ruled out. For a Play Store release this is a
small, known risk. Therefore:

- keep the pictures as a replaceable pack: each built-in exercise points to
  two image files by id, and nothing else depends on where they came from;
- add a short credits line for the dataset in the app (for example at the
  bottom of Profile).

**Building the list of about 150 (owner chose about 150, not all 876):**

- Choose the exercises most people do in a gym: mainly `strength`, the common
  `powerlifting` lifts, and the common `cardio` entries (treadmill, bike,
  elliptical, rowing, rope jumping, stair climber). Leave out stretching,
  strongman, olympic and plyometric entries unless very common.
- Only exercises that have both photos.
- Give each a short, familiar display name (for example "Bench Press" for
  `Barbell_Bench_Press_-_Medium_Grip`).
- Muscle group from `primaryMuscles`: chest → Chest; lats, middle back, lower
  back, traps → Back; shoulders → Shoulders; biceps, triceps, forearms → Arms;
  quadriceps, hamstrings, glutes, calves, adductors, abductors → Legs;
  abdominals → Core; `category` cardio → Cardio.
- Kind, assigned by hand for each chosen exercise, with these rules:
  - **cardio** if `category` is cardio;
  - **body weight** only for the pull-up and chin-up family, the dip family
    and the push-up family (note that some dips are listed with equipment
    "other", so the dataset's `equipment` field cannot decide this alone);
  - **reps only** for every other exercise done with no weight (`equipment`
    "body only"): crunches, sit-ups, leg raises, bodyweight squats and lunges
    and the like;
  - **weights** for everything else.
- Leave out timed holds such as the plank.
- Cover every muscle group reasonably, and include every exercise used by the
  starter routines.

**Pictures in the app (owner):** the two photos switch about once a second so
it reads as a simple GIF. They appear in the search list, on the open exercise
on Today, in the routine editor and on the detail page. Shrink and compress
them (about 400 px wide, WebP) and bundle them inside the app; aim for about
10 MB or less in total. If switching pictures in long lists hurts scrolling,
lists may show a still picture; Today and the detail page must switch.

Custom exercises use one of seven simple drawn icons, one per muscle group.

## 10. Starter content (my call; the owner asked for these five routines)

| Routine | Exercises |
|---|---|
| Full Body | Squat, Bench Press, Bent Over Row, Dumbbell Shoulder Press, Lat Pulldown, Dumbbell Bicep Curl, Triceps Pushdown |
| Chest + Triceps | Bench Press, Incline Dumbbell Press, Dumbbell Flyes, Triceps Pushdown, Overhead Triceps Extension |
| Back + Biceps | Lat Pulldown, Seated Cable Row, Bent Over Row, Barbell Curl, Hammer Curl |
| Leg + Shoulder | Squat, Leg Press, Leg Extension, Lying Leg Curl, Dumbbell Shoulder Press, Side Lateral Raise |
| Cardio | Treadmill, Stationary Bike |

- Starting values: 3 sets of 10 reps. Because numbers cannot be typed, a
  never-done weights exercise starts at a modest weight by equipment rather
  than 0: barbell 20 kg, dumbbell 5 kg, cable or machine 10 kg, anything else
  0 kg. Cardio starts at 10 minutes.
- Starting weekly schedule: Monday Chest + Triceps, Tuesday Back + Biceps,
  Wednesday Leg + Shoulder, Thursday Cardio, Friday Full Body, Saturday and
  Sunday rest. The owner changes it freely.

## 11. Decisions made by the designer, for the owner to check

1. A routine can mix weights, body weight and cardio exercises.
2. Switching today's routine does not change the weekly schedule.
3. Pressing and holding − or + repeats and speeds up.
4. "Add set" copies the last set. A set added today but never ticked is not
   kept for next time.
5. Body weight exercises count full body weight plus extra weight, and cannot
   be ticked until the profile has a body weight.
6. Editing an old workout in History does not change what the routine shows
   next time.
7. A newly added exercise starts from its latest numbers in any routine, or
   from 3 × 10 at a modest starting weight.
8. Record badges appear only when an earlier record is beaten.
9. One moving-average N for all charts.
10. Deleting a routine or a custom exercise keeps its history.
11. Importing a backup replaces all current data.
12. Profile body weight is typed, with − / + for 0.1 kg nudges.
13. Weeks run Monday to Sunday.
14. The starter routines' exercises, starting weights and starting schedule.
15. App id placeholder `app.overload.tracker`.
16. Reps only exercises: records are most reps in one set and in one session;
    they have no optional weight; bodyweight squats and lunges are reps only
    too.
17. Timed holds such as the plank are not in the built-in list.
18. Android's phone-to-phone transfer is switched off along with its cloud
    backup.
19. The weekly, cardio and body weight charts have no moving-average line
    (as in the approved mockup).

## 12. Known risks

- **Photo origin** (section 9). Accepted for now; the pack is replaceable.
- **Buttons-only numbers.** Large weight changes depend on press-and-hold
  feeling good. If it does not, the owner may want typing after all.
- **Play Store.** Publishing needs work outside this design: a final app id
  and name, signing, a store listing and a privacy statement.
