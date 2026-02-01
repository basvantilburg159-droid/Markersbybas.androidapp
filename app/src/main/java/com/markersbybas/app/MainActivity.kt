package com.markersbybas.app

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
        ensureLocationPermission(context)
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        color = Color(0xFF111111),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Online login",
                    style = MaterialTheme.typography.titleLarge,
                    color = BasYellow
                )
                Button(
                    onClick = { name = ""; password = "" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF333333),
                        contentColor = Color.White
                    )
                ) {
                    Text("Cancel")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Enter password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            if (vm.loginError.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = vm.loginError,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF5555),
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(
                onClick = { vm.login(name, password, context) },
                colors = ButtonDefaults.buttonColors(containerColor = BasYellow, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login")
            }
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
            vm.startHeadingUpdates(context)
        } else {
            vm.stopHeadingUpdates()
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
                    onClick = { exportKmz(context, fileName, vm.projectState.pts) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = BasYellow)
                ) {
                    Text("KMZ")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { showSettings = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = BasYellow)
                ) {
                    Text("⚙", fontSize = 20.sp)
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
                computeDeltaDistance(marker, markers.getOrNull(index + 1))
            }
            val accumulatedDistances = buildAccumulatedDistances(deltaDistances)
            markers.forEachIndexed { index: Int, marker: MarkerRow ->
                val prevName = markers.getOrNull(index - 1)?.name.orEmpty()
                val arrowLabel = if (prevName.isBlank()) marker.name else "$prevName → ${marker.name}"
                val deltaDistance = if (index == 0) 0.0 else computeDeltaDistance(markers[index - 1], marker)
                val accumulatedDistance = if (index == 0) 0.0 else accumulatedDistances[index - 1]
                val showDelta = index > 0

                MarkerBlock(
                    marker = marker,
                    arrowLabel = arrowLabel,
                    deltaDistance = deltaDistance,
                    accumulatedDistance = accumulatedDistance,
                    currentUserLocation = vm.currentUserLocation,
                    deviceHeading = vm.deviceHeading,
                    showDelta = showDelta,
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
    deviceHeading: Float?,
    showDelta: Boolean,
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
                    style = MaterialTheme.typography.headlineLarge,
                    color = actualColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (showDelta) {
                Text(
                    text = "Δ $arrowLabel ${formatDistance(deltaDistance)}m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BasYellow
                )
            }
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
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = "Open in Maps",
                                modifier = Modifier.size(20.dp)
                            )
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
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "Honing",
                                modifier = Modifier.size(20.dp)
                            )
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
                tonalElevation = 4.dp,
                color = Color.Black
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = marker.name, style = MaterialTheme.typography.titleMedium, color = BasYellow)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (currentUserLocation == null || !hasCoords) {
                        Text(
                            text = "Waiting for location...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BasYellow,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(100.dp))
                    } else {
                        val target = Location("marker").apply {
                            latitude = marker.lat!!
                            longitude = marker.lng!!
                        }
                        val distanceMeters = currentUserLocation.distanceTo(target)
                        val bearing = (currentUserLocation.bearingTo(target) + 360f) % 360f
                        val heading = deviceHeading
                        val arrowBearing = if (heading != null) {
                            (bearing - heading + 360f) % 360f
                        } else {
                            bearing
                        }

                        Text(
                            text = "Distance: ${formatDistance(distanceMeters.toDouble())} m",
                            style = MaterialTheme.typography.bodyLarge,
                            color = BasYellow
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Compass
                        CompassView(arrowBearing = arrowBearing)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${arrowBearing.toInt()}°",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BasYellow
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showHoningDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = BasYellow, contentColor = Color.Black)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun CompassView(arrowBearing: Float) {
    val compassSize = 200.dp
    
    Box(
        modifier = Modifier.size(compassSize),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(compassSize)
        ) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 - 25f
            
            // Draw outer circle
            drawCircle(
                color = BasYellow,
                radius = radius + 5f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
            
            // Draw tick marks
            for (i in 0 until 360 step 10) {
                val angleRad = Math.toRadians(i.toDouble() - 90).toFloat()
                val isMajor = i % 90 == 0
                val isMedium = i % 30 == 0
                val tickLength = when {
                    isMajor -> 0f  // Skip cardinal directions - we'll draw letters
                    isMedium -> 12f
                    else -> 6f
                }
                if (tickLength > 0) {
                    val startRadius = radius - tickLength
                    val endRadius = radius
                    
                    val startX = center.x + startRadius * kotlin.math.cos(angleRad)
                    val startY = center.y + startRadius * kotlin.math.sin(angleRad)
                    val endX = center.x + endRadius * kotlin.math.cos(angleRad)
                    val endY = center.y + endRadius * kotlin.math.sin(angleRad)
                    
                    drawLine(
                        color = BasYellow,
                        start = androidx.compose.ui.geometry.Offset(startX, startY),
                        end = androidx.compose.ui.geometry.Offset(endX, endY),
                        strokeWidth = if (isMedium) 2f else 1f
                    )
                }
            }
            
            // Draw arrow pointing to target (rotated by arrowBearing)
            val arrowAngleRad = Math.toRadians(arrowBearing.toDouble() - 90).toFloat()
            val arrowLength = radius - 35f
            
            // Arrow tip (red part - pointing to target)
            val tipX = center.x + arrowLength * kotlin.math.cos(arrowAngleRad)
            val tipY = center.y + arrowLength * kotlin.math.sin(arrowAngleRad)
            
            // Arrow base points (for triangle)
            val baseOffset = 15f
            val perpAngle = arrowAngleRad + (Math.PI / 2).toFloat()
            val base1X = center.x + baseOffset * kotlin.math.cos(perpAngle)
            val base1Y = center.y + baseOffset * kotlin.math.sin(perpAngle)
            val base2X = center.x - baseOffset * kotlin.math.cos(perpAngle)
            val base2Y = center.y - baseOffset * kotlin.math.sin(perpAngle)
            
            // Draw red arrow (pointing to target)
            val redPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(tipX, tipY)
                lineTo(base1X, base1Y)
                lineTo(center.x, center.y)
                close()
            }
            drawPath(redPath, color = Color(0xFFE53935))
            
            val redPath2 = androidx.compose.ui.graphics.Path().apply {
                moveTo(tipX, tipY)
                lineTo(base2X, base2Y)
                lineTo(center.x, center.y)
                close()
            }
            drawPath(redPath2, color = Color(0xFFE53935))
            
            // Draw white/gray arrow (opposite direction)
            val tailAngleRad = arrowAngleRad + Math.PI.toFloat()
            val tailX = center.x + arrowLength * kotlin.math.cos(tailAngleRad)
            val tailY = center.y + arrowLength * kotlin.math.sin(tailAngleRad)
            
            val whitePath = androidx.compose.ui.graphics.Path().apply {
                moveTo(tailX, tailY)
                lineTo(base1X, base1Y)
                lineTo(center.x, center.y)
                close()
            }
            drawPath(whitePath, color = Color.White)
            
            val whitePath2 = androidx.compose.ui.graphics.Path().apply {
                moveTo(tailX, tailY)
                lineTo(base2X, base2Y)
                lineTo(center.x, center.y)
                close()
            }
            drawPath(whitePath2, color = Color.White)
            
            // Draw center dot
            drawCircle(
                color = BasYellow,
                radius = 6f,
                center = center
            )
        }
        
        // Cardinal directions positioned on top of the compass
        Text("N", color = BasYellow, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        Text("S", color = BasYellow, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        Text("E", color = BasYellow, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp))
        Text("W", color = BasYellow, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp))
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

private fun exportKmz(context: Context, fileName: String, markers: List<MarkerRow>) {
    android.util.Log.d("KML_EXPORT", "exportKmz called with ${markers.size} markers")
    markers.forEach { m ->
        android.util.Log.d("KML_EXPORT", "Marker: ${m.name}, lat=${m.lat}, lng=${m.lng}")
    }
    
    val kmlContent = buildKml(markers, fileName)
    android.util.Log.d("KML_EXPORT", "Generated KML: $kmlContent")

    val kmlFile = File(context.cacheDir, "doc.kml")
    val kmzFile = File(context.cacheDir, "$fileName.kmz")

    try {
        kmlFile.writer().use { it.write(kmlContent) }

        ZipOutputStream(FileOutputStream(kmzFile)).use { zipOut ->
            val zipEntry = ZipEntry(kmlFile.name)
            zipOut.putNextEntry(zipEntry)
            kmlFile.inputStream().use { it.copyTo(zipOut) }
            zipOut.closeEntry()
        }

        openGeoFile(context, kmzFile)
    } catch (e: Exception) {
        android.util.Log.e("KML_EXPORT", "Error exporting KMZ", e)
    } finally {
        if (kmlFile.exists()) kmlFile.delete()
    }
}

private fun openGeoFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    android.util.Log.d("KML_EXPORT", "Sharing URI: $uri")

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.google-earth.kmz")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val chooser = Intent.createChooser(intent, "Open with").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val resInfoList = context.packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY)
    for (resolveInfo in resInfoList) {
        val packageName = resolveInfo.activityInfo.packageName
        context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.util.Log.e("KML_EXPORT", "Failed to open KMZ file", e)
        // Fallback to a generic send intent if view fails
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "application/vnd.google-earth.kmz"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(sendIntent, "Share KMZ").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e2: Exception) {
            android.util.Log.e("KML_EXPORT", "Also failed to share KMZ file", e2)
        }
    }
}

private fun buildKml(markers: List<MarkerRow>, name: String): String {
    val coords = markers.mapNotNull { marker ->
        val lat = marker.lat
        val lng = marker.lng
        if (lat == null || lng == null) return@mapNotNull null
        val latStr = String.format(java.util.Locale.US, "%.6f", lat)
        val lngStr = String.format(java.util.Locale.US, "%.6f", lng)
        Triple(latStr, lngStr, marker.name)
    }

    val placemarksList = coords.map { (latStr, lngStr, markerName) ->
        "<Placemark><name>${escapeXml(markerName)}</name><Point><coordinates>$lngStr,$latStr,0</coordinates></Point></Placemark>"
    }

    val firstCoord = coords.firstOrNull()
    val lookAtStr = if (firstCoord != null) {
        "<LookAt><longitude>${firstCoord.second}</longitude><latitude>${firstCoord.first}</latitude><range>1000</range></LookAt>"
    } else ""

    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">")
    sb.append("<Document>")
    sb.append("<name>${escapeXml(name)}</name>")
    sb.append("<open>1</open>")
    sb.append(lookAtStr)
    placemarksList.forEach { sb.append(it) }
    sb.append("</Document>")
    sb.append("</kml>")
    
    return sb.toString()
}

private fun escapeXml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
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
