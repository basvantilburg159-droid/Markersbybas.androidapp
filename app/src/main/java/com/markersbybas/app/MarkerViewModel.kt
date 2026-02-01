package com.markersbybas.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

    private var locationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    fun loadSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME_VM, Context.MODE_PRIVATE)
        showMapButtons = prefs.getBoolean(PREF_SHOW_MAP, true)
        showHoningButtons = prefs.getBoolean(PREF_SHOW_HONING, false)
        val user = auth.currentUser
        if (user != null) {
            isAuthed = true
            firebaseOnline = true
            onlineUser = "Guest"
            loadCloudFiles()
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    val anonUser = result.user
                    if (anonUser != null) {
                        isAuthed = true
                        firebaseOnline = true
                        onlineUser = "Guest"
                        loadCloudFiles()
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
            loadCloudFiles()
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    isAuthed = true
                    firebaseOnline = true
                    onlineUser = name.trim().ifBlank { "Guest" }
                    loadCloudFiles()
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
        firestore.collection(FIRESTORE_MARKERFILES)
            .document(id)
            .get()
            .addOnSuccessListener { snapshot ->
                val state = snapshot.get("state") as? Map<*, *>
                val pts = parseMarkerRows(state?.get("pts") ?: snapshot.get("pts"))
                projectState = ProjectState(pts = pts)
            }
            .addOnFailureListener { error ->
                loginError = error.localizedMessage ?: "Unable to load session."
            }
    }

    fun updateMarker(index: Int, time: String, isMissed: Boolean) {
        val updated = projectState.pts.mapIndexed { i, marker ->
            if (i == index) marker.copy(actualTime = time, missed = isMissed) else marker
        }
        projectState = projectState.copy(pts = updated)
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

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                currentUserLocation = result.lastLocation
            }
        }
        locationCallback = callback
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    override fun onCleared() {
        super.onCleared()
        locationCallback?.let { callback ->
            locationClient?.removeLocationUpdates(callback)
        }
        locationCallback = null
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

    private fun parseMarkerRows(raw: Any?): List<MarkerRow> {
        val rows = raw as? List<*> ?: return emptyList()
        return rows.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            MarkerRow(
                name = map["name"] as? String ?: map["markername"] as? String ?: "",
                expectedTime = map["expectedTime"] as? String ?: map["expectedtime"] as? String ?: "",
                actualTime = map["actualTime"] as? String ?: map["time"] as? String ?: "",
                missed = map["missed"] as? Boolean ?: false,
                lat = (map["lat"] as? Number)?.toDouble()
                    ?: (map["latitude"] as? Number)?.toDouble(),
                lng = (map["lng"] as? Number)?.toDouble()
                    ?: (map["longitude"] as? Number)?.toDouble()
            )
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
    val lng: Double? = null
)

data class ProjectState(
    val pts: List<MarkerRow> = emptyList()
)

fun computeDeltaDistance(current: MarkerRow, previous: MarkerRow?): Double {
    if (previous?.lat == null || previous.lng == null || current.lat == null || current.lng == null) {
        return 0.0
    }
    val previousLocation = Location("previous").apply {
        latitude = previous.lat
        longitude = previous.lng
    }
    val currentLocation = Location("current").apply {
        latitude = current.lat
        longitude = current.lng
    }
    return previousLocation.distanceTo(currentLocation).toDouble()
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
