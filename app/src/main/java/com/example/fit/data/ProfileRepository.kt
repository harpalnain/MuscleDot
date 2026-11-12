package com.example.fit.data

import com.example.fit.data.db.AppDatabase
import com.example.fit.data.db.toDomain
import com.example.fit.data.db.toEntity
import com.example.fit.domain.BodyWeightEntry
import com.example.fit.domain.DEFAULT_AVERAGE_N
import com.example.fit.domain.MAX_AVERAGE_N
import com.example.fit.domain.MIN_AVERAGE_N
import com.example.fit.domain.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** The profile, the body weight history and the moving-average setting (spec 5.10, 5.12). */
class ProfileRepository(db: AppDatabase) {
    private val dao = db.profileDao()

    /** An empty profile until one is saved. */
    fun observeProfile(): Flow<Profile> = dao.observeProfile().map { it?.toDomain() ?: Profile() }.distinctUntilChanged()

    suspend fun saveProfile(profile: Profile) = dao.upsertProfile(profile.toEntity())

    /** Ascending by date, one entry per day. */
    fun observeBodyWeights(): Flow<List<BodyWeightEntry>> =
        dao.observeBodyWeights().map { list -> list.map { BodyWeightEntry(it.date, it.weightHg) } }

    /** The most recent body weight, or null if none was ever entered. */
    suspend fun currentBodyWeightHg(): Int? = dao.latestBodyWeightHg()

    /** A second call on the same date replaces the first. A workout on that date takes the new value. */
    suspend fun setBodyWeight(date: LocalDate, weightHg: Int) = dao.setBodyWeight(date, weightHg)

    /** N of the moving average, shared by every chart. */
    fun observeAverageN(): Flow<Int> =
        dao.observeSetting(AVERAGE_N_KEY).map { it?.toIntOrNull() ?: DEFAULT_AVERAGE_N }.distinctUntilChanged()

    suspend fun setAverageN(n: Int) =
        dao.putSetting(AVERAGE_N_KEY, n.coerceIn(MIN_AVERAGE_N, MAX_AVERAGE_N).toString())

    companion object {
        const val AVERAGE_N_KEY = "averageN"
    }
}
