package com.ambientsense.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambientsense.app.ui.screens.CalibrateScreen
import com.ambientsense.app.ui.screens.DevicesScreen
import com.ambientsense.app.ui.screens.FieldScreen
import com.ambientsense.app.ui.screens.MapScreen
import com.ambientsense.app.ui.screens.SettingsScreen
import com.ambientsense.app.ui.screens.SignalScreen
import com.ambientsense.app.ui.screens.ValidationScreen
import com.ambientsense.app.ui.theme.AmbientPalette
import com.ambientsense.app.util.formatDuration

enum class Screen(val label: String, val icon: ImageVector) {
    FIELD("Field", Icons.Outlined.Analytics),
    MAP("Map", Icons.Outlined.Map),
    DEVICES("Devices", Icons.Outlined.Bluetooth),
    SIGNAL("Signal", Icons.Outlined.NetworkCheck),
    CALIBRATE("Calibrate", Icons.Outlined.Tune),
    VALIDATE("Accuracy", Icons.Outlined.Timeline)
}

@Composable
fun AppRoot(vm: AmbientViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.FIELD) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val running by vm.running.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val sessionStart by vm.sessionStartMs.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (showSettings) "Settings" else screen.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (running) AmbientPalette.GoodColor else AmbientPalette.SlateLight
                            )
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = if (running) {
                            "$status · ${formatDuration(System.currentTimeMillis() - sessionStart)}"
                        } else status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = { showSettings = !showSettings }) {
                Icon(
                    imageVector = if (showSettings) Icons.Outlined.Analytics else Icons.Outlined.Settings,
                    contentDescription = if (showSettings) "Back to field" else "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Crossfade(targetState = showSettings to screen, label = "screen") { (settings, dest) ->
                if (settings) {
                    SettingsScreen(vm = vm, onBack = { showSettings = false })
                } else when (dest) {
                    Screen.FIELD -> FieldScreen(vm)
                    Screen.MAP -> MapScreen(vm)
                    Screen.DEVICES -> DevicesScreen(vm)
                    Screen.SIGNAL -> SignalScreen(vm)
                    Screen.CALIBRATE -> CalibrateScreen(vm)
                    Screen.VALIDATE -> ValidationScreen(vm)
                }
            }
        }

        if (!showSettings) {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp
            ) {
                Screen.entries.forEach { dest ->
                    val selected = dest == screen
                    NavigationBarItem(
                        selected = selected,
                        onClick = { screen = dest },
                        icon = {
                            Icon(dest.icon, contentDescription = dest.label, modifier = Modifier.size(21.dp))
                        },
                        label = {
                            Text(dest.label, style = MaterialTheme.typography.labelSmall)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ScreenContainer(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = verticalArrangement,
        content = content
    )
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp)
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
