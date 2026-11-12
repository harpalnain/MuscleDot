package com.example.fit.data

import com.example.fit.data.db.AppDatabase
import com.example.fit.data.db.ExerciseEntity
import com.example.fit.data.db.toDomain
import com.example.fit.domain.Exercise
import com.example.fit.domain.ExerciseKind
import com.example.fit.domain.MuscleGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Built-in and custom exercises (spec 5.1). Built-in ones are written by the Seeder. */
class ExerciseRepository(db: AppDatabase) {
    private val dao = db.exerciseDao()

    /** Every exercise that can be picked, sorted by name. Hidden ones are left out. */
    fun observeAll(): Flow<List<Exercise>> = dao.observeVisible().map { list -> list.map { it.toDomain() } }

    /** Finds hidden exercises too, because history still refers to them. */
    suspend fun get(id: String): Exercise? = dao.get(id)?.toDomain()

    /** Returns the new exercise's id. Custom exercises have no picture. */
    suspend fun createCustom(name: String, group: MuscleGroup, kind: ExerciseKind): String {
        val id = CUSTOM_ID_PREFIX + UUID.randomUUID()
        dao.insert(
            ExerciseEntity(
                id = id,
                name = name.trim(),
                muscleGroup = group,
                muscle = null,
                equipment = null,
                kind = kind,
                isCustom = true,
                isHidden = false,
                imageBase = null,
            ),
        )
        return id
    }

    /** Ignored for built-in exercises. */
    suspend fun renameCustom(id: String, name: String) = dao.renameCustom(id, name.trim())

    /** Ignored for built-in exercises. An exercise with history is only hidden. */
    suspend fun deleteCustom(id: String) = dao.deleteCustom(id)

    companion object {
        const val CUSTOM_ID_PREFIX = "custom-"
    }
}
