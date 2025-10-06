package com.example.touristquiz.data

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import java.util.concurrent.TimeUnit

class UserLocationRepository(private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    fun uploadLocation(uid: String, lat: Double, lng: Double, profileImageUrl: String?) {
        val doc = firestore.collection("user_locations").document(uid)
        val data = mapOf(
            "lat" to lat,
            "lng" to lng,
            "profileImageUrl" to profileImageUrl,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        doc.set(data)
    }

    fun deleteLocation(uid: String) {
        firestore.collection("user_locations").document(uid)
            .delete()
    }

    data class UserLocation(val uid: String, val latLng: LatLng, val profileImageUrl: String?)

    fun addLocationsListener(
        onlineWindowMillis: Long = TimeUnit.MINUTES.toMillis(2),
        onChange: (List<UserLocation>) -> Unit
    ): ListenerRegistration {
        return firestore.collection("user_locations")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val now = System.currentTimeMillis()
                val result = mutableListOf<UserLocation>()
                for (doc in snapshot.documents) {
                    val id = doc.id
                    val lat = doc.getDouble("lat")
                    val lng = doc.getDouble("lng")
                    val imageUrl = doc.getString("profileImageUrl")
                    val ts: Timestamp? = doc.getTimestamp("updatedAt")
                    val updatedAtMs = ts?.toDate()?.time
                    val isOnline = updatedAtMs != null && (now - updatedAtMs) <= onlineWindowMillis
                    if (lat != null && lng != null && isOnline) {
                        result.add(UserLocation(id, LatLng(lat, lng), imageUrl))
                    }
                }
                onChange(result)
            }
    }
}
