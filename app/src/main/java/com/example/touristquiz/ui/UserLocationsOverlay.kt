package com.example.touristquiz.ui

import android.Manifest
import android.graphics.Bitmap
import android.location.Location
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.CircleCropTransformation
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import com.example.touristquiz.data.UserLocationRepository
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun UserLocationsOverlay(
    currentUserId: String?,
    repository: UserLocationRepository = UserLocationRepository(),
    onMyLocationUpdated: (LatLng) -> Unit = {},
    // New: center and distance filtering
    centerLatLng: LatLng? = null,
    limitByDistance: Boolean = false,
    maxDistanceMeters: Float = 0f,
) {
    val context = LocalContext.current

    // Permisije za lokaciju
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Fused location client i moj marker
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val myMarkerState = remember { MarkerState() }

    // Moja slika
    var myProfileImageUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentUserId) {
        val uid = currentUserId ?: return@LaunchedEffect
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                myProfileImageUrl = snap.getString("profileImageUrl")
            }
            .addOnFailureListener { e ->
                Log.w("UserLocationsOverlay", "Failed to load profile image url: ${e.message}")
            }
    }

    // Realtime lokacije (ostali)
    var locations by remember { mutableStateOf<List<UserLocationRepository.UserLocation>>(emptyList()) }
    DisposableEffect(Unit) {
        val registration = repository.addLocationsListener { list ->
            locations = list
        }
        onDispose { registration.remove() }
    }

    // Markeri i ikonice (ostali)
    val markerStates = remember { mutableStateMapOf<String, MarkerState>() }
    val bitmapCache = remember { mutableStateMapOf<String, Bitmap?>() }

    // Primenjen filter po distanci za druge korisnike
    val filteredOthers = remember(locations, centerLatLng, limitByDistance, maxDistanceMeters, currentUserId) {
        val base = locations.filter { it.uid != currentUserId }
        if (!limitByDistance || centerLatLng == null) base else {
            base.filter { loc ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    centerLatLng.latitude, centerLatLng.longitude,
                    loc.latLng.latitude, loc.latLng.longitude,
                    results
                )
                results[0] <= maxDistanceMeters
            }
        }
    }

    // Sinhronizacija markera sa filtriranim lokacijama (ukloni nestale ili van dometa)
    val presentIds = remember(filteredOthers) { filteredOthers.map { it.uid }.toSet() }
    LaunchedEffect(presentIds) {
        val toRemove = markerStates.keys - presentIds
        toRemove.forEach {
            markerStates.remove(it)
            bitmapCache.remove(it)
        }
    }

    // Kada dobijemo dozvole, pocni location updates i upload moje lokacije
    DisposableEffect(locationPermissions.permissions.any { it.status.isGranted }, fusedLocationClient, currentUserId, myProfileImageUrl) {
        val anyGranted = locationPermissions.permissions.any { it.status.isGranted }
        if (anyGranted && currentUserId != null) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
                .setMinUpdateIntervalMillis(5_000L)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc: Location? = result.lastLocation
                    if (loc != null) {
                        val latLng = LatLng(loc.latitude, loc.longitude)
                        myMarkerState.position = latLng
                        onMyLocationUpdated(latLng)
                        // Upload moje lokacije
                        runCatching {
                            repository.uploadLocation(currentUserId, latLng.latitude, latLng.longitude, myProfileImageUrl)
                        }.onFailure { e -> Log.w("UserLocationsOverlay", "uploadLocation failed: ${e.message}") }
                    }
                }
            }
            try {
                fusedLocationClient.requestLocationUpdates(request, callback, null)
            } catch (se: SecurityException) {
                Log.w("UserLocationsOverlay", "Location permission missing: ${se.message}")
            }
            onDispose { fusedLocationClient.removeLocationUpdates(callback) }
        } else {
            onDispose { /* no-op */ }
        }
    }

    // Ako nema dozvola, zatraži ih
    LaunchedEffect(Unit) {
        if (!locationPermissions.permissions.any { it.status.isGranted }) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // Prikaz markera - ostali (filtrirani)
    filteredOthers.forEach { userLoc ->
        val state = markerStates.getOrPut(userLoc.uid) { MarkerState(position = userLoc.latLng) }
        LaunchedEffect(userLoc.uid, userLoc.latLng) { state.position = userLoc.latLng }

        val imageKey = userLoc.profileImageUrl ?: userLoc.uid
        val cached = bitmapCache[imageKey]
        LaunchedEffect(imageKey) {
            if (bitmapCache.containsKey(imageKey)) return@LaunchedEffect
            val url = userLoc.profileImageUrl
            if (url != null) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .transformations(CircleCropTransformation())
                        .allowHardware(false)
                        .build()
                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val original = result.drawable.toBitmap()
                        val scaled = original.scale(64, 64)
                        bitmapCache[imageKey] = scaled
                    } else {
                        Log.d("UserLocationsOverlay", "Image load failed for $url")
                        bitmapCache[imageKey] = null
                    }
                } catch (e: Exception) {
                    Log.w("UserLocationsOverlay", "Avatar load error: ${e.message}")
                    bitmapCache[imageKey] = null
                }
            } else {
                bitmapCache[imageKey] = null
            }
        }

        if (cached != null) {
            Marker(state = state, icon = BitmapDescriptorFactory.fromBitmap(cached))
        } else {
            Marker(state = state)
        }
    }

    // Moj marker
    val myImageKey = (myProfileImageUrl ?: currentUserId) ?: "me"
    val myCached = bitmapCache[myImageKey]
    LaunchedEffect(myImageKey) {
        if (bitmapCache.containsKey(myImageKey)) return@LaunchedEffect
        val url = myProfileImageUrl
        if (url != null) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .transformations(CircleCropTransformation())
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val original = result.drawable.toBitmap()
                    val scaled = original.scale(100, 100)
                    bitmapCache[myImageKey] = scaled
                } else bitmapCache[myImageKey] = null
            } catch (e: Exception) {
                Log.w("UserLocationsOverlay", "My avatar load error: ${e.message}")
                bitmapCache[myImageKey] = null
            }
        } else {
            bitmapCache[myImageKey] = null
        }
    }
    if (myCached != null) {
        Marker(state = myMarkerState, icon = BitmapDescriptorFactory.fromBitmap(myCached))
    } else {
        Marker(state = myMarkerState)
    }
}
