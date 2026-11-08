package com.loganlink.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.loganlink.LocalApp
import com.loganlink.R
import com.loganlink.ui.screens.BatteryScreen
import com.loganlink.ui.screens.DeviceScreen
import com.loganlink.ui.screens.DiagnosticsScreen
import com.loganlink.ui.screens.DriveModeScreen
import com.loganlink.ui.screens.DriversScreen
import com.loganlink.ui.screens.EngineScreen
import com.loganlink.ui.screens.FuelScreen
import com.loganlink.ui.screens.HealthScreen
import com.loganlink.ui.screens.LiveScreen
import com.loganlink.ui.screens.MaintenanceScreen
import com.loganlink.ui.screens.MoreScreen
import com.loganlink.ui.screens.OnboardingScreen
import com.loganlink.ui.screens.OverviewScreen
import com.loganlink.ui.screens.ParkingScreen
import com.loganlink.ui.screens.SettingsScreen
import com.loganlink.ui.screens.TripDetailScreen
import com.loganlink.ui.screens.TripsScreen
import com.loganlink.ui.screens.VehicleScreen
import kotlinx.coroutines.flow.SharedFlow

object Routes {
    const val HOME = "home"; const val ONBOARD = "onboard"; const val DRIVE = "drive"
    const val TRIP = "trip/{id}"; fun trip(id: Long) = "trip/$id"
    const val DIAG = "diagnostics"; const val BATTERY = "battery"; const val ENGINE = "engine"
    const val MAINT = "maintenance"; const val FUEL = "fuel"; const val PARKING = "parking"
    const val DEVICE = "device"; const val VEHICLE = "vehicle"; const val DRIVERS = "drivers"; const val SETTINGS = "settings"
}

private data class Tab(val label: String, val icon: Int)
private val tabs = listOf(
    Tab("Car", R.drawable.car), Tab("Live", R.drawable.gauge), Tab("Trips", R.drawable.route),
    Tab("Health", R.drawable.heart), Tab("Garage", R.drawable.tools),
)

@Composable
fun AppNav(openDrive: SharedFlow<Unit>) {
    val app = LocalApp.current
    val pairing by app.prefs.pairing.collectAsStateWithLifecycle()
    val demo by app.prefs.demo.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val start = if (pairing != null || demo) Routes.HOME else Routes.ONBOARD
    LaunchedEffect(openDrive) { openDrive.collect { nav.navigate(Routes.DRIVE) { launchSingleTop = true } } }

    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    NavHost(
        nav, startDestination = start,
        enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(280)) { it / 10 } },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { it / 10 } },
    ) {
        composable(Routes.ONBOARD) {
            OnboardingScreen(onDone = { nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARD) { inclusive = true } } })
        }
        composable(Routes.HOME) { HomeShell(nav) }
        composable(Routes.DRIVE) { DriveModeScreen(onExit = { nav.popBackStack() }) }
        composable(Routes.TRIP, listOf(navArgument("id") { type = NavType.LongType })) {
            TripDetailScreen(it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
        }
        composable(Routes.DIAG) { DiagnosticsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.BATTERY) { BatteryScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.ENGINE) { EngineScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.MAINT) { MaintenanceScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.FUEL) { FuelScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PARKING) { ParkingScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.DEVICE) { DeviceScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.VEHICLE) { VehicleScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.DRIVERS) { DriversScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() }, onForgotten = {
                nav.navigate(Routes.ONBOARD) { popUpTo(0) { inclusive = true } }
            })
        }
    }
    }
}

@Composable
private fun HomeShell(nav: NavHostController) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()
    BackHandler(enabled = selected != 0) { selected = 0 }
    val go: (String) -> Unit = { nav.navigate(it) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = selected == i, onClick = { selected = i },
                            icon = {
                                val s by androidx.compose.animation.core.animateFloatAsState(if (selected == i) 1.12f else 1f,
                                    androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 500f), label = "tab")
                                Icon(painterResource(t.icon), t.label, Modifier.size(22.dp).graphicsLayer { scaleX = s; scaleY = s })
                            },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            androidx.compose.animation.AnimatedContent(selected, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "tabs") { tab ->
            holder.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> OverviewScreen(go, onTab = { selected = it })
                    1 -> LiveScreen(go)
                    2 -> TripsScreen(go)
                    3 -> HealthScreen(go)
                    else -> MoreScreen(go)
                }
            }
            }
        }
    }
}
