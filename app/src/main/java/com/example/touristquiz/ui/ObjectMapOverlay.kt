package com.example.touristquiz.ui

import androidx.compose.runtime.Composable
import com.example.touristquiz.data.ObjectRepository.TouristObject
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState

@Composable
fun ObjectMapOverlay(
    objects: List<TouristObject>,
    onObjectClick: (TouristObject) -> Unit
) {
    fun hueForType(type: String?): Float = when (type?.lowercase()) {
        "cultural" -> BitmapDescriptorFactory.HUE_ORANGE
        "historical" -> BitmapDescriptorFactory.HUE_GREEN
        else -> BitmapDescriptorFactory.HUE_AZURE // tourist attraction or unknown
    }

    objects.forEach { obj ->
        Marker(
            state = rememberMarkerState(position = obj.latLng),
            title = obj.name,
            snippet = obj.ownerName?.let { "by $it" } ?: "",
            icon = BitmapDescriptorFactory.defaultMarker(hueForType(obj.type)),
            onClick = {
                onObjectClick(obj)
                true
            }
        )
    }
}
