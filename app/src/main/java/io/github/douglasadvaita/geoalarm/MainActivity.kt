package io.github.douglasadvaita.geoalarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.douglasadvaita.geoalarm.ui.AddEditScreen
import io.github.douglasadvaita.geoalarm.ui.AllMapScreen
import io.github.douglasadvaita.geoalarm.ui.AlarmViewModel
import io.github.douglasadvaita.geoalarm.ui.HistoryScreen
import io.github.douglasadvaita.geoalarm.ui.ListScreen
import io.github.douglasadvaita.geoalarm.ui.TripsScreen
import io.github.douglasadvaita.geoalarm.ui.theme.GeoAlarmTheme
import io.github.douglasadvaita.geoalarm.util.Notifications
import io.github.douglasadvaita.geoalarm.util.TrackPrefs
import io.github.douglasadvaita.geoalarm.service.WatchService
import io.github.douglasadvaita.geoalarm.receiver.TrackCompactionReceiver
import io.github.douglasadvaita.geoalarm.data.AppDatabase
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: AlarmViewModel by viewModels()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.ensureChannels(this)
        TrackCompactionReceiver.schedule(this)
        askPerms()
        // Al abrir la app: reactiva vencidas y arranca vigilancia si hay habilitadas
        vm.reactivateExpired()
        autoStart()
        setContent {
            GeoAlarmTheme {
                AppNav(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.reactivateExpired()
        autoStart()
    }

    private fun autoStart() {
        lifecycleScope.launch(Dispatchers.IO) {
            val enabled = AppDatabase.get(this@MainActivity).alarmDao().getEnabled()
            if (enabled.isNotEmpty() || TrackPrefs.enabled(this@MainActivity)) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(android.content.Intent(this@MainActivity, WatchService::class.java).apply {
                            action = WatchService.ACTION_START
                        })
                    } else {
                        startService(android.content.Intent(this@MainActivity, WatchService::class.java).apply {
                            action = WatchService.ACTION_START
                        })
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun askPerms() {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = list.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }
}

@Composable
fun AppNav(vm: AlarmViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            ListScreen(vm,
                onAdd = { nav.navigate("add/0") },
                onEdit = { id -> nav.navigate("add/$id") },
                onMap = { nav.navigate("allmap") },
                onTrips = { nav.navigate("trips") },
                onHistory = { nav.navigate("history") })
        }
        composable("allmap") {
            AllMapScreen(vm, onBack = { nav.popBackStack() })
        }
        composable("trips") {
            TripsScreen(onBack = { nav.popBackStack() })
        }
        composable("history") {
            HistoryScreen(onBack = { nav.popBackStack() })
        }
        composable("add/{id}") { back ->
            val id = back.arguments?.getString("id")?.toLongOrNull() ?: 0L
            AddEditScreen(vm, editId = if (id == 0L) null else id, onDone = { nav.popBackStack() })
        }
    }
}
