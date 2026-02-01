package com.markersbybas.app

import android.app.Activity
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.widget.NumberPicker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.delay

val BasYellow = Color(0xFFF6B000)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        setContent {
            val colorScheme = darkColorScheme(
                primary = BasYellow,
                onPrimary = Color.Black,
                primaryContainer = BasYellow,
                onPrimaryContainer = Color.Black,
                secondary = BasYellow,
                onSecondary = Color.Black,
                secondaryContainer = Color(0xFF333333),
                onSecondaryContainer = Color.White,
                tertiary = BasYellow,
                onTertiary = Color.Black,
                background = Color(0xFF1A1A1A),
                onBackground = BasYellow,
                surface = Color(0xFF1E1E1E),
                onSurface = BasYellow,
                surfaceVariant = Color(0xFF2D2D2D),
                onSurfaceVariant = BasYellow,
                outline = BasYellow,
                error = Color(0xFFCF6679),
                onError = Color.Black
            )

            MaterialTheme(colorScheme = colorScheme) {
                val vm: MarkerViewModel = viewModel()
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MarkerApp(vm, this)
                }
            }
        }
    }
}

@Composable
fun MarkerApp(vm: MarkerViewModel, context: Context) {
    LaunchedEffect(Unit) {
        vm.loadSettings(context)
    }

    var showLogin by remember { mutableStateOf(false) }
    var showMarkerList by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1500)
        showLogin = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        AnimatedVisibility(
            visible = !showLogin,
            enter = fadeIn(animationSpec = tween(600)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            AnimatedTitle()
        }

        AnimatedVisibility(
            visible = showLogin,
            enter = fadeIn(animationSpec = tween(600))
        ) {
            when {
                !vm.isAuthed -> LoginForm(vm = vm, context = context)
                showMarkerList -> MarkerListScreen(
                    vm = vm,
                    context = context,
                    onBack = { showMarkerList = false }
                )
                else -> SignedInScreen(
                    vm = vm,
                    context = context,
                    onLoadFile = {
                        val selectedId = vm.selectedCloudId
                        if (selectedId.isNotBlank()) {
                            vm.loadCloudProject(selectedId)
                            showMarkerList = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AnimatedTitle() {
    val transition = rememberInfiniteTransition(label = "titlePulse")
    val alpha by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200))
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Markers by Bas",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.alpha(alpha)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = BasYellow
        )
    }
}

@Composable
private fun LoginForm(vm: MarkerViewModel, context: Context) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Sign in", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { vm.login(name, password, context) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = BasYellow)
        ) {
            Text("Sign in")
        }
        if (vm.loginError.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = vm.loginError,
                style = MaterialTheme.typography.bodySmall,
                color = BasYellow,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignedInScreen(
    vm: MarkerViewModel,
    context: Context,
    onLoadFile: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = vm.cloudFiles.firstOrNull { file: CloudFile -> file.id == vm.selectedCloudId }?.name.orEmpty()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Signed in as ${vm.onlineUser}",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { vm.logout(context) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = BasYellow)
        ) {
            Text("Sign out")
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (vm.cloudFiles.isEmpty()) {
            Text(
                text = "No cloud files loaded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = BasYellow,
                textAlign = TextAlign.Center
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select file") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    vm.cloudFiles.forEach { file: CloudFile ->
                        DropdownMenuItem(
                            text = { Text(file.name) },
                            onClick = {
                                expanded = false
                                vm.selectCloudProject(file.id)
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onLoadFile,
                enabled = vm.selectedCloudId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = BasYellow)
            ) {
                Text("Load file")
            }
        }
    }
}

@Composable
private fun MarkerListScreen(vm: MarkerViewModel, context: Context, onBack: () -> Unit) {
    val markers: List<MarkerRow> = vm.projectState.pts
    val fileName = vm.cloudFiles.firstOrNull { file: CloudFile -> file.id == vm.selectedCloudId }?.name ?: "Loaded file"
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(vm.showHoningButtons) {
        if (vm.showHoningButtons) {
            ensureLocationPermission(context)
            vm.startLocationUpdates(context)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleMedium,
                color = BasYellow
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeartbeatIndicator(isOnline = vm.firebaseOnline)
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { showSettings = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = BasYellow)
                ) {
                    Text("⚙")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = BasYellow)
        ) {
            Text("Back")
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (markers.isEmpty()) {
            Text(
                text = "No markers loaded yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = BasYellow,
                textAlign = TextAlign.Center
            )
        } else {
            val deltaDistances = markers.mapIndexed { index: Int, marker: MarkerRow ->
                computeDeltaDistance(marker, markers.getOrNull(index - 1))
            }
            val accumulatedDistances = buildAccumulatedDistances(deltaDistances)
            markers.forEachIndexed { index: Int, marker: MarkerRow ->
                val prevName = markers.getOrNull(index - 1)?.name.orEmpty()
                val arrowLabel = if (prevName.isBlank()) marker.name else "$prevName → ${marker.name}"
                val deltaDistance = deltaDistances[index]
                val accumulatedDistance = accumulatedDistances[index]

                MarkerBlock(
                    marker = marker,
                    arrowLabel = arrowLabel,
                    deltaDistance = deltaDistance,
                    accumulatedDistance = accumulatedDistance,
                    currentUserLocation = vm.currentUserLocation,
                    showMapButton = vm.showMapButtons,
                    showHoningButton = vm.showHoningButtons,
                    onRequestLocation = {
                        ensureLocationPermission(context)
                        vm.startLocationUpdates(context)
                    },
                    onTimeChange = { time -> vm.updateMarker(index, time, isMissed = false) },
                    onNotDetected = { vm.updateMarker(index, time = "", isMissed = true) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            showMapButtons = vm.showMapButtons,
            showHoningButtons = vm.showHoningButtons,
            onMapToggle = { vm.toggleMapButtons(context, it) },
            onHoningToggle = { vm.toggleHoningButtons(context, it) },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun SettingsDialog(
    showMapButtons: Boolean,
    showHoningButtons: Boolean,
    onMapToggle: (Boolean) -> Unit,
    onHoningToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Settings", style = MaterialTheme.typography.titleMedium, color = BasYellow)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Show Google Maps", color = BasYellow)
                    Switch(checked = showMapButtons, onCheckedChange = onMapToggle)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Show Honing", color = BasYellow)
                    Switch(checked = showHoningButtons, onCheckedChange = onHoningToggle)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = BasYellow)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun MarkerBlock(
    marker: MarkerRow,
    arrowLabel: String,
    deltaDistance: Double,
    accumulatedDistance: Double,
    currentUserLocation: Location?,
    showMapButton: Boolean,
    showHoningButton: Boolean,
    onRequestLocation: () -> Unit,
    onTimeChange: (String) -> Unit,
    onNotDetected: () -> Unit
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var showHoningDialog by remember { mutableStateOf(false) }
    val placeholder = "--:--"
    val actualDisplay = when {
        marker.missed -> "Not Detected"
        marker.actualTime.isBlank() -> placeholder
        else -> marker.actualTime
    }
    val actualColor = if (marker.missed) Color(0xFFD32F2F) else BasYellow
    val hasCoords = marker.lat != null && marker.lng != null

    Surface(
        color = Color.Black,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = marker.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = BasYellow
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Expected",
                        style = MaterialTheme.typography.bodySmall,
                        color = BasYellow
                    )
                    Text(
                        text = marker.expectedTime,
                        style = MaterialTheme.typography.headlineSmall,
                        color = BasYellow
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPicker = true }
            ) {
                Text(
                    text = "Actual (tap to edit)",
                    style = MaterialTheme.typography.bodySmall,
                    color = BasYellow
                )
                Text(
                    text = actualDisplay,
                    style = MaterialTheme.typography.titleLarge,
                    color = actualColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Δ $arrowLabel ${formatDistance(deltaDistance)}m",
                style = MaterialTheme.typography.bodyMedium,
                color = BasYellow
            )
            Text(
                text = "Total: ${formatDistance(accumulatedDistance)}m",
                style = MaterialTheme.typography.bodyMedium,
                color = BasYellow
            )
            if (showMapButton || showHoningButton) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showMapButton) {
                        Button(
                            onClick = {
                                if (hasCoords) openGoogleMaps(context, marker.lat!!, marker.lng!!, marker.name)
                            },
                            enabled = hasCoords,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = BasYellow)
                        ) {
                            Text("Maps")
                        }
                    }
                    if (showHoningButton) {
                        Button(
                            onClick = {
                                if (hasCoords) {
                                    onRequestLocation()
                                    showHoningDialog = true
                                }
                            },
                            enabled = hasCoords,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = BasYellow)
                        ) {
                            Text("Honing")
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        Dialog(onDismissRequest = { showPicker = false }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = marker.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = BasYellow
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TimeWheelPicker(
                        timeValue = marker.actualTime,
                        onTimeChange = onTimeChange
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                onNotDetected()
                                showPicker = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = BasYellow)
                        ) {
                            Text("Not detected")
                        }
                        Button(
                            onClick = { showPicker = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = BasYellow)
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }

    if (showHoningDialog) {
        Dialog(onDismissRequest = { showHoningDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Honing", style = MaterialTheme.typography.titleMedium, color = BasYellow)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (currentUserLocation == null || !hasCoords) {
                        Text(
                            text = "Waiting for location...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BasYellow,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        val target = Location("marker").apply {
                            latitude = marker.lat!!
                            longitude = marker.lng!!
                        }
                        val distanceMeters = currentUserLocation.distanceTo(target)
                        val bearing = (currentUserLocation.bearingTo(target) + 360f) % 360f

                        Text(
                            text = "Distance: ${formatDistance(distanceMeters.toDouble())} m",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BasYellow
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "➤",
                            fontSize = 96.sp,
                            color = BasYellow,
                            modifier = Modifier.graphicsLayer(rotationZ = bearing)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${bearing.toInt()}°",
                            style = MaterialTheme.typography.bodySmall,
                            color = BasYellow
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showHoningDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = BasYellow)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun HeartbeatIndicator(isOnline: Boolean) {
    val transition = rememberInfiniteTransition(label = "heartbeat")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(800))
    )
    val color = if (isOnline) Color(0xFF4CAF50) else Color(0xFFD32F2F)

    Box(
        modifier = Modifier
            .size(14.dp)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}

@Composable
private fun TimeWheelPicker(
    timeValue: String,
    onTimeChange: (String) -> Unit
) {
    val (initialHour, initialMinute) = parseHourMinute(timeValue)
    var hour by remember(timeValue) { mutableStateOf(initialHour) }
    var minute by remember(timeValue) { mutableStateOf(initialMinute) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        AndroidView(
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = 0
                    maxValue = 23
                    value = hour
                    setOnValueChangedListener { _, _, newVal ->
                        hour = newVal
                        onTimeChange("${formatTwoDigits(hour)}:${formatTwoDigits(minute)}")
                    }
                }
            },
            update = { it.value = hour }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = ":", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.width(8.dp))
        AndroidView(
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = 0
                    maxValue = 59
                    value = minute
                    setOnValueChangedListener { _, _, newVal ->
                        minute = newVal
                        onTimeChange("${formatTwoDigits(hour)}:${formatTwoDigits(minute)}")
                    }
                }
            },
            update = { it.value = minute }
        )
    }
}

// ... rest of file unchanged
