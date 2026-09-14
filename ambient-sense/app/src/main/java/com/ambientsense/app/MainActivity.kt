package com.ambientsense.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambientsense.app.ui.AmbientViewModel
import com.ambientsense.app.ui.AppRoot
import com.ambientsense.app.ui.theme.AmbientTheme

class MainActivity : ComponentActivity() {

    private val vm: AmbientViewModel by viewModels()

    /** Set when the OS denied something we cannot live without. */
    private var missingPermissions by mutableStateOf<List<String>>(emptyList())

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            missingPermissions = results.filter { !it.value }.map { it.key }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AmbientTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val settings by vm.settings.collectAsStateWithLifecycle()
                    KeepScreenOn(enabled = settings.keepScreenOn)
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        missingPermissions = missingPermissions()
        if (missingPermissions.isNotEmpty()) permissionLauncher.launch(missingPermissions.toTypedArray())
    }

    private fun missingPermissions(): List<String> {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
            needed += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed += Manifest.permission.BLUETOOTH_SCAN
        } else {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        needed += Manifest.permission.ACCESS_FINE_LOCATION
        needed += Manifest.permission.RECORD_AUDIO
        return needed.distinct().filter {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, it
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    val window = (context as? ComponentActivity)?.window ?: return
    androidx.compose.runtime.DisposableEffect(enabled) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/** Convenience: jump to the OS location settings when GPS is switched off. */
fun openLocationSettings(context: android.content.Context) {
    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
