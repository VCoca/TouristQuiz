package com.example.touristquiz.location

import android.app.*
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.touristquiz.MainActivity
import com.example.touristquiz.data.UserLocationRepository
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import android.os.Looper
import com.google.firebase.firestore.QuerySnapshot

class ProximityService : Service() {
    private val channelId = "proximity_channel"
    private val notifId = 1001
    private val thresholdMeters = 30f
    private val objectThresholdMeters = 30f

    private lateinit var fusedClient: FusedLocationProviderClient
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val repo = UserLocationRepository(firestore)
    private val notifiedAt = ConcurrentHashMap<String, Long>()
    private val notifiedObjectAt = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.d("ProximityService", "Service started")
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
        startForeground(notifId, buildForegroundNotification())
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(5000).setPriority(Priority.PRIORITY_HIGH_ACCURACY).build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.w("ProximityService", "Missing location permission, stopping service", e)
            stopSelf()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            scope.launch {
                checkUserProximity(loc)
                checkObjectProximity(loc)
                // upload own location with profileImageUrl from Firestore
                auth.currentUser?.uid?.let { uid ->
                    val userDoc = firestore.collection("users").document(uid).get().await()
                    val profileImageUrl = userDoc.getString("profileImageUrl")
                    repo.uploadLocation(uid, loc.latitude, loc.longitude, profileImageUrl)
                }
            }
        }
    }

    private suspend fun checkUserProximity(myLoc: Location) {
        try {
            val snapshot = firestore.collection("user_locations").get().await()
            val meUid = auth.currentUser?.uid
            val now = System.currentTimeMillis()
            snapshot.documents.forEach { doc ->
                val otherUid = doc.id
                if (otherUid == meUid) return@forEach
                val name = doc.getString("name")?: return@forEach
                val lat = doc.getDouble("lat") ?: return@forEach
                val lng = doc.getDouble("lng") ?: return@forEach
                val results = FloatArray(1)
                Location.distanceBetween(myLoc.latitude, myLoc.longitude, lat, lng, results)
                val distance = results[0]
                val last = notifiedAt[otherUid] ?: 0L
                if (distance <= thresholdMeters && now - last > 5 * 60_000L) { // notify at most every 5 minutes per user
                    notifiedAt[otherUid] = now
                    sendUserProximityNotification(otherUid, name, distance.toInt())
                }
            }
        } catch (_: Exception) { /* ignore transient errors */ }
    }

    private suspend fun checkObjectProximity(myLoc: Location) {
        try {
            val snap: QuerySnapshot = firestore.collection("objects").get().await()
            val now = System.currentTimeMillis()
            for (doc in snap.documents) {
                val objId = doc.id
                val name = doc.getString("name") ?: continue
                val lat = doc.getDouble("lat") ?: continue
                val lng = doc.getDouble("lng") ?: continue
                val results = FloatArray(1)
                Location.distanceBetween(myLoc.latitude, myLoc.longitude, lat, lng, results)
                val distance = results[0]
                val last = notifiedObjectAt[objId] ?: 0L
                if (distance <= objectThresholdMeters && now - last > 10 * 60_000L) { // na svakih 10 minuta
                    notifiedObjectAt[objId] = now
                    sendObjectProximityNotification(objId, name, distance.toInt())
                }
            }
        } catch (e: Exception) {
            Log.w("ProximityService", "checkObjectProximity failed: ${e.message}")
        }
    }

    private fun sendUserProximityNotification(userId : String, name: String, meters: Int) {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Drugi korisnik je blizu")
            .setContentText("Korisnik $name je oko $meters m daleko")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(2000 + (userId.hashCode() and 0xFFF), notif)
    }

    private fun sendObjectProximityNotification(objectId: String, name: String, meters: Int) {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Objekat u blizini")
            .setContentText("$name je oko $meters m daleko")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(3000 + (objectId.hashCode() and 0xFFF), notif)
    }

    private fun buildForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Proximity Service")
            .setContentText("Proximity monitoring active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pending)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(channelId, "Proximity", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedClient.removeLocationUpdates(locationCallback)
        scope.cancel()
    }
}
