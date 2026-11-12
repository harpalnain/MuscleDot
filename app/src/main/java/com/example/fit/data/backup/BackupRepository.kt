package com.example.fit.data.backup

import androidx.room.withTransaction
import com.example.fit.data.Seeder
import com.example.fit.data.db.AppDatabase
import com.example.fit.data.db.BodyWeightEntity
import com.example.fit.data.db.ExerciseEntity
import com.example.fit.data.db.ProfileEntity
import com.example.fit.data.db.RoutineEntity
import com.example.fit.data.db.RoutineExerciseEntity
import com.example.fit.data.db.RoutineSetEntity
import com.example.fit.data.db.ScheduleDayEntity
import com.example.fit.data.db.SettingEntity
import com.example.fit.data.db.WorkoutEntity
import com.example.fit.data.db.WorkoutExerciseEntity
import com.example.fit.data.db.WorkoutSetEntity
import com.example.fit.domain.Profile
import java.time.LocalDate
import java.time.LocalDateTime

/** Export and import of the whole of the user's data as one file (spec 5.13). */
class BackupRepository(private val db: AppDatabase) {
    private val dao = db.backupDao()

    /** The backup file's text. [now] is recorded in it. */
    suspend fun export(now: LocalDateTime): String = db.withTransaction {
        val profile = db.profileDao().getProfile()
        BackupCodec.encode(
            BackupFile(
                exportedAt = now.toString(),
                profile = BackupProfile(
                    name = profile?.name ?: Profile().name,
                    dateOfBirth = profile?.dateOfBirth?.toString(),
                    heightFeet = profile?.heightFeet,
                    heightInches = profile?.heightInches,
                    welcomeDone = profile?.welcomeDone ?: false,
                ),
                bodyWeights = dao.bodyWeights().map { BackupBodyWeight(it.date.toString(), it.weightHg) },
                // The seed version describes this app's built-in list, not the user's data.
                settings = dao.settings().filter { it.key != Seeder.SEED_VERSION_KEY }.associate { it.key to it.value },
                exercises = dao.exercisesToBackUp().map {
                    BackupExercise(it.id, it.name, it.muscleGroup, it.muscle, it.equipment, it.kind, it.isCustom, it.isHidden, it.imageBase)
                },
                routines = dao.routines().map { routine ->
                    BackupRoutine(
                        id = routine.routine.id,
                        name = routine.routine.name,
                        exercises = routine.exercises.sortedWith(compareBy({ it.link.position }, { it.link.id })).map { exercise ->
                            BackupRoutineExercise(
                                exerciseId = exercise.link.exerciseId,
                                sets = exercise.sets.sortedWith(compareBy({ it.position }, { it.id }))
                                    .map { BackupSet(it.weightHg, it.reps, it.minutes) },
                            )
                        },
                    )
                },
                schedule = dao.schedule().map { BackupScheduleDay(it.dayOfWeek, it.routineId) },
                workouts = dao.workouts().map { workout ->
                    BackupWorkout(
                        id = workout.workout.id,
                        date = workout.workout.date.toString(),
                        routineId = workout.workout.routineId,
                        routineName = workout.workout.routineName,
                        status = workout.workout.status,
                        bodyWeightHg = workout.workout.bodyWeightHg,
                        exercises = workout.exercises.sortedWith(compareBy({ it.link.position }, { it.link.id })).map { exercise ->
                            BackupWorkoutExercise(
                                exerciseId = exercise.link.exerciseId,
                                sets = exercise.sets.sortedWith(compareBy({ it.position }, { it.id }))
                                    .map { BackupSet(it.weightHg, it.reps, it.minutes, it.done) },
                            )
                        },
                    )
                },
            ),
        )
    }

    /**
     * Replaces all of the user's data with the file's. The file is checked first, and
     * the replacement is one transaction, so a refused or inconsistent file leaves
     * everything as it was. Throws [BackupException].
     */
    suspend fun import(text: String) {
        val file = BackupCodec.decode(text)
        try {
            db.withTransaction { replaceAll(file) }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            // A bad date or a reference to something the file does not contain.
            throw BackupException(BackupException.Reason.NOT_A_BACKUP)
        }
    }

    private suspend fun replaceAll(file: BackupFile) {
        dao.deleteWorkouts()
        dao.deleteSchedule()
        dao.deleteRoutines()
        dao.deleteBodyWeights()
        dao.deleteProfile()
        dao.deleteSettingsExcept(Seeder.SEED_VERSION_KEY)
        dao.deleteCustomExercises()

        // Built-in exercises this app ships stay as they are. An exercise the app does
        // not have comes from the file: a custom one as it was, any other one hidden
        // and without a picture, so its history survives.
        val known = dao.exerciseIds().toSet()
        dao.insertExercises(
            file.exercises.filter { it.id !in known }.map {
                ExerciseEntity(
                    id = it.id,
                    name = it.name,
                    muscleGroup = it.group,
                    muscle = it.muscle,
                    equipment = it.equipment,
                    kind = it.kind,
                    isCustom = it.isCustom,
                    isHidden = if (it.isCustom) it.isHidden else true,
                    imageBase = if (it.isCustom) it.imageBase else null,
                )
            },
        )

        db.profileDao().upsertProfile(
            ProfileEntity(
                name = file.profile.name,
                dateOfBirth = file.profile.dateOfBirth?.let(LocalDate::parse),
                heightFeet = file.profile.heightFeet,
                heightInches = file.profile.heightInches,
                welcomeDone = file.profile.welcomeDone,
            ),
        )
        dao.insertBodyWeights(file.bodyWeights.map { BodyWeightEntity(LocalDate.parse(it.date), it.weightHg) })
        dao.insertSettings(
            file.settings.filterKeys { it != Seeder.SEED_VERSION_KEY }.map { (key, value) -> SettingEntity(key, value) },
        )

        val routineDao = db.routineDao()
        file.routines.forEachIndexed { index, routine ->
            routineDao.insertRoutine(RoutineEntity(id = routine.id, name = routine.name, position = index))
            routine.exercises.forEachIndexed { exerciseIndex, exercise ->
                val linkId = routineDao.insertExercise(
                    RoutineExerciseEntity(routineId = routine.id, exerciseId = exercise.exerciseId, position = exerciseIndex),
                )
                routineDao.insertSets(
                    exercise.sets.mapIndexed { setIndex, set ->
                        RoutineSetEntity(
                            routineExerciseId = linkId,
                            position = setIndex,
                            weightHg = set.weightHg,
                            reps = set.reps,
                            minutes = set.minutes,
                        )
                    },
                )
            }
        }
        dao.insertSchedule(file.schedule.map { ScheduleDayEntity(it.dayOfWeek, it.routineId) })

        val workoutDao = db.workoutDao()
        for (workout in file.workouts) {
            workoutDao.insertWorkout(
                WorkoutEntity(
                    id = workout.id,
                    date = LocalDate.parse(workout.date),
                    routineId = workout.routineId,
                    routineName = workout.routineName,
                    status = workout.status,
                    bodyWeightHg = workout.bodyWeightHg,
                ),
            )
            workout.exercises.forEachIndexed { exerciseIndex, exercise ->
                val linkId = workoutDao.insertExercise(
                    WorkoutExerciseEntity(workoutId = workout.id, exerciseId = exercise.exerciseId, position = exerciseIndex),
                )
                workoutDao.insertSets(
                    exercise.sets.mapIndexed { setIndex, set ->
                        WorkoutSetEntity(
                            workoutExerciseId = linkId,
                            position = setIndex,
                            weightHg = set.weightHg,
                            reps = set.reps,
                            minutes = set.minutes,
                            done = set.done,
                        )
                    },
                )
            }
        }
    }
}
