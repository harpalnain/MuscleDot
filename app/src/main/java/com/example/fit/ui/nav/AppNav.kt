package com.example.fit.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.fit.R
import com.example.fit.ui.appContainer
import com.example.fit.ui.components.LocalFlipFrame
import com.example.fit.ui.profile.ProfileScreen
import com.example.fit.ui.profile.WelcomeScreen
import com.example.fit.ui.routines.RoutinesScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private const val FLIP_INTERVAL_MS = 1000L

private data class Tab(
    val route: Any,
    val routeClass: KClass<*>,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val tag: String,
)

private val TABS = listOf(
    Tab(TodayRoute, TodayRoute::class, R.string.tab_today, R.drawable.ic_tab_today, "tab-today"),
    Tab(RoutinesRoute, RoutinesRoute::class, R.string.tab_routines, R.drawable.ic_tab_routines, "tab-routines"),
    Tab(ProgressRoute, ProgressRoute::class, R.string.tab_progress, R.drawable.ic_tab_progress, "tab-progress"),
    Tab(HistoryRoute, HistoryRoute::class, R.string.tab_history, R.drawable.ic_tab_history, "tab-history"),
    Tab(ProfileRoute, ProfileRoute::class, R.string.tab_profile, R.drawable.ic_tab_profile, "tab-profile"),
)

/** The whole app: five tabs along the bottom and the screens reached from them. */
@Composable
fun AppNav() {
    val container = appContainer()
    val scope = rememberCoroutineScope()

    // Coming to the front is what moves the app to a new day and saves a workout
    // left over from an earlier day (spec 5.6).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { container.onForeground() }
    }

    // Null while the app is still starting; nothing is shown until then.
    val showWelcome = container.startOnWelcome.collectAsStateWithLifecycle().value
    if (showWelcome == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val navController = rememberNavController()
    CompositionLocalProvider(LocalFlipFrame provides flipFrame()) {
        Scaffold(bottomBar = { TabBar(navController) }) { padding ->
            NavHost(
                navController = navController,
                startDestination = if (showWelcome) WelcomeRoute else TodayRoute,
                modifier = Modifier.padding(padding),
            ) {
                composable<WelcomeRoute> {
                    WelcomeScreen(
                        onFinished = { navController.navigate(TodayRoute) { popUpTo<WelcomeRoute> { inclusive = true } } },
                    )
                }
                composable<TodayRoute> { Placeholder("screen-today") }
                composable<RoutinesRoute> {
                    RoutinesScreen(onEdit = { navController.navigate(RoutineEditorRoute(it)) })
                }
                composable<ProgressRoute> { Placeholder("screen-progress") }
                composable<HistoryRoute> { Placeholder("screen-history") }
                composable<ProfileRoute> { ProfileScreen() }
                composable<RoutineEditorRoute> { Placeholder("screen-routine-editor") }
                composable<ExercisePickerRoute> { Placeholder("screen-exercise-picker") }
                composable<ExerciseDetailRoute> { Placeholder("screen-exercise-detail") }
                composable<WorkoutDetailRoute> { Placeholder("screen-workout-detail") }
            }
        }
    }
}

/** 0 or 1, switching once a second while the app is in front. */
@Composable
private fun flipFrame(): Int {
    val lifecycleOwner = LocalLifecycleOwner.current
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(FLIP_INTERVAL_MS)
                frame = 1 - frame
            }
        }
    }
    return frame
}

@Composable
private fun TabBar(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    // The bar belongs to the five tab screens only; screens opened from them are full height.
    if (TABS.none { destination?.hasRoute(it.routeClass) == true }) return
    NavigationBar {
        for (tab in TABS) {
            NavigationBarItem(
                selected = destination?.hasRoute(tab.routeClass) == true,
                onClick = {
                    navController.navigate(tab.route) {
                        // Today is the root: switching tabs never stacks them up.
                        popUpTo<TodayRoute> { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                modifier = Modifier.testTag(tab.tag),
            )
        }
    }
}

/** Stands in for a screen that a later change adds; the tag stays on the real screen's root. */
@Composable
private fun Placeholder(tag: String) {
    Box(Modifier.fillMaxSize().testTag(tag))
}
