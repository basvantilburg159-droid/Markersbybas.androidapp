package com.markersbybas.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

private const val PREFS_NAME_VM = "markersbybas"
private const val PREF_SHOW_MAP = "show_map_buttons"
private const val PREF_SHOW_HONING = "show_honing_buttons"
private const val REQUEST_LOCATION_VM = 1001
private const val FIRESTORE_MARKERFILES = "markerfiles"

class MarkerViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    var isAuthed by mutableStateOf(false)
        private set
    var loginError by mutableStateOf("")
        private set
    var cloudFiles: List<CloudFile> by mutableStateOf(emptyList())
        private set
    var selectedCloudId by mutableStateOf("")
        private set
    var projectState by mutableStateOf(ProjectState())
        private set
    var currentUserLocation by mutableStateOf<Location?>(null)
        private set
    var showMapButtons by mutableStateOf(true)
        private set
    var showHoningButtons by mutableStateOf(false)
        private set
    var firebaseOnline by mutableStateOf(false)
        private set
    var onlineUser by mutableStateOf("")
        private set
    var deviceHeading by mutableStateOf<Float?>(null)
        private set

    private var locationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var cloudFilesListener: ListenerRegistration? = null
    private var cloudProjectListener: ListenerRegistration? = null
    private var sensorManager: SensorManager? = null
    private var headingListener: SensorEventListener? = null

    fun loadSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME_VM, Context.MODE_PRIVATE)
        showMapButtons = prefs.getBoolean(PREF_SHOW_MAP, true)
        showHoningButtons = prefs.getBoolean(PREF_SHOW_HONING, true)
        val user = auth.currentUser
        if (user != null) {
            isAuthed = true
            firebaseOnline = true
            onlineUser = "Guest"
            startCloudFilesListener()
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    val anonUser = result.user
                    if (anonUser != null) {
                        isAuthed = true
                        firebaseOnline = true
                        onlineUser = "Guest"
                        startCloudFilesListener()
                    } else {
                        firebaseOnline = false
                    }
                }
                .addOnFailureListener { error ->
                    loginError = error.localizedMessage ?: "Authentication failed."
                    firebaseOnline = false
                }
        }
    }

    fun login(name: String, password: String, context: Context) {
        loginError = ""
        val current = auth.currentUser
        if (current != null) {
            isAuthed = true
            firebaseOnline = true
            onlineUser = name.trim().ifBlank { "Guest" }
            startCloudFilesListener()
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    isAuthed = true
                    firebaseOnline = true
                    onlineUser = name.trim().ifBlank { "Guest" }
                    startCloudFilesListener()
                } else {
                    loginError = "Authentication failed."
                    firebaseOnline = false
                }
            }
            .addOnFailureListener { error ->
                loginError = error.localizedMessage ?: "Authentication failed."
                firebaseOnline = false
            }
    }

    fun logout(context: Context) {
        auth.signOut()
        isAuthed = false
        onlineUser = ""
        firebaseOnline = false
        cloudFiles = emptyList()
        selectedCloudId = ""
        projectState = ProjectState()
    }

    fun selectCloudProject(id: String) {
        selectedCloudId = id
    }

    fun loadCloudProject(id: String) {
        selectedCloudId = id
        cloudProjectListener?.remove()
        cloudProjectListener = firestore.collection(FIRESTORE_MARKERFILES)
            .document(id)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    loginError = error.localizedMessage ?: "Unable to load session."
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val state = snapshot.get("state") as? Map<*, *>
                val pts = parseMarkerRows(state?.get("pts") ?: snapshot.get("pts"))
                projectState = ProjectState(pts = pts)
            }
    }

    fun updateMarker(index: Int, time: String, isMissed: Boolean) {
        val updated = projectState.pts.mapIndexed { i, marker ->
            if (i == index) marker.copy(actualTime = time, missed = isMissed) else marker
        }
        projectState = projectState.copy(pts = shiftExpectedTimes(updated, index, time, isMissed))
        saveCloudProject()
    }

    fun toggleMapButtons(context: Context, enabled: Boolean) {
        showMapButtons = enabled
        context.getSharedPreferences(PREFS_NAME_VM, Context.MODE_PRIVATE).edit {
            putBoolean(PREF_SHOW_MAP, enabled)
        }
    }

    fun toggleHoningButtons(context: Context, enabled: Boolean) {
        showHoningButtons = enabled
        context.getSharedPreferences(PREFS_NAME_VM, Context.MODE_PRIVATE).edit {
            putBoolean(PREF_SHOW_HONING, enabled)
        }
    }

    fun startLocationUpdates(context: Context) {
        val activity = context as? Activity ?: return
        if (ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val client = locationClient ?: LocationServices.getFusedLocationProviderClient(activity)
        locationClient = client
        if (locationCallback != null) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 250L)
            .setMinUpdateIntervalMillis(0L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                currentUserLocation = result.lastLocation
            }
        }
        locationCallback = callback
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    fun startHeadingUpdates(context: Context) {
        if (headingListener != null) return
        val manager = sensorManager ?: context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager = manager
        val rotationSensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val azimuthRad = orientation[0]
                val azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                deviceHeading = (azimuthDeg + 360f) % 360f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        headingListener = listener
        manager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stopHeadingUpdates() {
        val manager = sensorManager
        val listener = headingListener
        if (manager != null && listener != null) {
            manager.unregisterListener(listener)
        }
        headingListener = null
    }

    override fun onCleared() {
        super.onCleared()
        locationCallback?.let { callback ->
            locationClient?.removeLocationUpdates(callback)
        }
        locationCallback = null
        cloudFilesListener?.remove()
        cloudFilesListener = null
        cloudProjectListener?.remove()
        cloudProjectListener = null
        stopHeadingUpdates()
    }

    private fun loadCloudFiles() {
        firestore.collection(FIRESTORE_MARKERFILES)
            .get()
            .addOnSuccessListener { snapshot ->
                cloudFiles = snapshot.documents.map { doc ->
                    CloudFile(
                        id = doc.id,
                        name = doc.getString("name") ?: doc.id
                    )
                }
                if (cloudFiles.isNotEmpty() && selectedCloudId.isBlank()) {
                    selectedCloudId = cloudFiles.first().id
                }
            }
            .addOnFailureListener { error ->
                loginError = error.localizedMessage ?: "Unable to load sessions."
            }
    }

    private fun startCloudFilesListener() {
        if (cloudFilesListener != null) return
        cloudFilesListener = firestore.collection(FIRESTORE_MARKERFILES)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    loginError = error.localizedMessage ?: "Unable to load sessions."
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                cloudFiles = snapshot.documents.map { doc ->
                    CloudFile(
                        id = doc.id,
                        name = doc.getString("name") ?: doc.id
                    )
                }
                if (cloudFiles.isNotEmpty() && selectedCloudId.isBlank()) {
                    selectedCloudId = cloudFiles.first().id
                }
            }
        loadCloudFiles()
    }

    private fun parseMarkerRows(raw: Any?): List<MarkerRow> {
        val rows = raw as? List<*> ?: return emptyList()
        return rows.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            MarkerRow(
                name = map["name"] as? String ?: map["markername"] as? String ?: "",
                expectedTime = map["expectedTime"] as? String ?: map["expectedtime"] as? String ?: "",
                actualTime = map["actualTime"] as? String ?: map["time"] as? String ?: "",
                missed = map["missed"] as? Boolean ?: false,
                lat = parseDouble(map["lat"]) ?: parseDouble(map["latitude"]),
                lng = parseDouble(map["lng"]) ?: parseDouble(map["longitude"]),
                distance = parseDouble(map["distance"]) ?: parseDouble(map["dist"])
            )
        }
    }

    private fun parseDouble(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> {
                val trimmed = value.trim()
                if (trimmed.isBlank()) null
                else trimmed.replace(',', '.').toDoubleOrNull()
            }
            else -> null
        }
    }

    private fun shiftExpectedTimes(
        pts: List<MarkerRow>,
        index: Int,
        actualTime: String,
        isMissed: Boolean
    ): List<MarkerRow> {
        if (isMissed || actualTime.isBlank()) return pts
        val expectedAtIndex = pts.getOrNull(index)?.expectedTime ?: return pts
        val expectedSec = parseTimeToSeconds(expectedAtIndex) ?: return pts
        val actualSec = parseTimeToSeconds(actualTime) ?: return pts
        val deltaSec = actualSec - expectedSec
        if (deltaSec == 0) return pts

        return pts.mapIndexed { i, marker ->
            if (i <= index) marker
            else {
                val markerExpected = parseTimeToSeconds(marker.expectedTime)
                if (markerExpected == null) marker
                else marker.copy(expectedTime = formatTimeFromSeconds(markerExpected + deltaSec))
            }
        }
    }

    private fun saveCloudProject() {
        val docId = selectedCloudId
        if (docId.isBlank()) return
        if (!isAuthed) return

        val pts = projectState.pts.map { marker ->
            mutableMapOf<String, Any?>(
                "markername" to marker.name,
                "expectedtime" to marker.expectedTime,
                "time" to marker.actualTime,
                "missed" to marker.missed,
                "latitude" to marker.lat,
                "longitude" to marker.lng
            ).apply {
                if (marker.distance != null) {
                    this["distance"] = marker.distance
                }
            }
        }

        val payload = mapOf(
            "state" to mapOf("pts" to pts),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection(FIRESTORE_MARKERFILES)
            .document(docId)
            .set(payload, com.google.firebase.firestore.SetOptions.merge())
            .addOnFailureListener { error ->
                loginError = error.localizedMessage ?: "Unable to save session."
            }
    }
}

data class CloudFile(
    val id: String,
    val name: String
)

data class MarkerRow(
    val name: String,
    val expectedTime: String,
    val actualTime: String = "",
    val missed: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val distance: Double? = null
)

data class ProjectState(
    val pts: List<MarkerRow> = emptyList()
)

fun computeDeltaDistance(current: MarkerRow, next: MarkerRow?): Double {
    if (current.distance != null) {
        return current.distance
    }
    if (next?.lat == null || next.lng == null || current.lat == null || current.lng == null) {
        return 0.0
    }
    val currentLocation = Location("current").apply {
        latitude = current.lat
        longitude = current.lng
    }
    val nextLocation = Location("next").apply {
        latitude = next.lat
        longitude = next.lng
    }
    return currentLocation.distanceTo(nextLocation).toDouble()
}

fun buildAccumulatedDistances(deltaDistances: List<Double>): List<Double> {
    var runningTotal = 0.0
    return deltaDistances.map { distance ->
        runningTotal += distance
        runningTotal
    }
}

fun formatDistance(distanceMeters: Double): String {
    return String.format(Locale.US, "%.1f", distanceMeters)
}

fun parseHourMinute(timeValue: String): Pair<Int, Int> {
    val parts = timeValue.split(":")
    if (parts.size == 2) {
        val hour = parts[0].toIntOrNull() ?: 0
        val minute = parts[1].toIntOrNull() ?: 0
        return hour to minute
    }
    return 0 to 0
}

fun parseTimeToSeconds(timeValue: String): Int? {
    val parts = timeValue.trim().split(":")
    if (parts.size < 2) return null
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
    val second = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return hour * 3600 + minute * 60 + second
}

fun formatTimeFromSeconds(totalSeconds: Int): String {
    val normalized = ((totalSeconds % 86400) + 86400) % 86400
    val hour = normalized / 3600
    val minute = (normalized % 3600) / 60
    val second = normalized % 60
    return "${formatTwoDigits(hour)}:${formatTwoDigits(minute)}:${formatTwoDigits(second)}"
}

fun formatTwoDigits(value: Int): String = String.format(Locale.US, "%02d", value)

fun openGoogleMaps(context: Context, lat: Double, lng: Double, label: String) {
    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.setPackage("com.google.android.apps.maps")
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

fun ensureLocationPermission(context: Context) {
    val activity = context as? Activity ?: return
    if (ActivityCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_LOCATION_VM
        )
    }
}
