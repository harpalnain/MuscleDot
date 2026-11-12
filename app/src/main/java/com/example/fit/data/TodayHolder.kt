package com.example.fit.data

import com.example.fit.domain.AppClock
import com.example.fit.domain.appDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * The current app day (04:00 to 03:59). It only changes when [refresh] is called,
 * which the app does every time it comes to the front.
 */
class TodayHolder(private val clock: AppClock) {
    private val current = MutableStateFlow(appDay(clock.now()))
    val date: StateFlow<LocalDate> = current.asStateFlow()

    fun refresh() {
        current.value = appDay(clock.now())
    }
}
