package com.example.touristquiz

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.touristquiz.ui.login.LoginScreen
import com.example.touristquiz.ui.login.RegisterScreen
import com.example.touristquiz.data.AuthRepository
import com.example.touristquiz.data.ImageRepository
import com.example.touristquiz.ui.MapScreen
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.MapsInitializer
import com.google.firebase.auth.FirebaseAuth
import androidx.core.content.ContextCompat
import com.example.touristquiz.location.ProximityService
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.example.touristquiz.data.UserLocationRepository
import com.example.touristquiz.ui.LeaderboardScreen

class MainActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private val locationRepo by lazy { UserLocationRepository() }
    private var lastSignedInUid: String? = null

    // Pitaj za notifikacije (API 33+)
    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {  }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inicijalizacija Maps SDK
        try {
            MapsInitializer.initialize(applicationContext)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "MapsInitializer.initialize failed: ${e.message}")
        }

        // Trazi dozvolu za notifikacije ako je API 33+
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Start/stop proximity service
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                lastSignedInUid = user.uid
                val fineGranted = ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val coarseGranted = ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!fineGranted && !coarseGranted) {
                    android.util.Log.i("MainActivity", "Skipping ProximityService start: location permission not granted")
                } else {
                    try {
                        val intent = Intent(this, ProximityService::class.java)
                        ContextCompat.startForegroundService(this, intent)
                    } catch (e: Exception) {
                        android.util.Log.w("MainActivity", "Failed to start ProximityService: ${e.message}")
                    }
                }
            } else {
                // Ako se korisnik odjavi, ukloni lokaciju i zaustavi servis
                lastSignedInUid?.let { uid ->
                    locationRepo.deleteLocation(uid)
                    lastSignedInUid = null
                }
                try {
                    val intent = Intent(this, ProximityService::class.java)
                    stopService(intent)
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Failed to stop ProximityService: ${e.message}")
                }
            }
        }
        auth.addAuthStateListener(authListener!!)

        val imageRepo = ImageRepository(this)
        val authRepository = AuthRepository(imageRepo)
        setContent {
            AuthApp(authRepository = authRepository, imageRepository = imageRepo)
        }
    }

    override fun onDestroy() {
        // Auto logout i brisanje lokacije ako se activity gasi (ne radi se o rotaciji)
        if (isFinishing && !isChangingConfigurations) {
            try {
                val uid = auth.currentUser?.uid ?: lastSignedInUid
                if (uid != null) {
                    locationRepo.deleteLocation(uid)
                }
                // Stop servis
                try {
                    val intent = Intent(this, ProximityService::class.java)
                    stopService(intent)
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Failed to stop service in onDestroy: ${e.message}")
                }
                // Sign out
                auth.signOut()
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Sign out/cleanup failed in onDestroy: ${e.message}")
            }
        }
        // Ukloni listener
        authListener?.let { auth.removeAuthStateListener(it) }
        super.onDestroy()
    }
}

@Composable
fun AuthApp(authRepository: AuthRepository, imageRepository: ImageRepository) {
    val navController = rememberNavController()
    val ctx = LocalContext.current

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onRegisterClick = { navController.navigate("register") },
                onLoginSuccess = {
                    try {
                        navController.navigate("map") {
                            popUpTo("login") { inclusive = true }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Navigation failed: ${e.message}", Toast.LENGTH_LONG).show()
                        e.printStackTrace()
                    }
                },
                authRepository = authRepository
            )
        }
        composable("register") {
            RegisterScreen(
                onLoginClick = { navController.popBackStack() },
                authRepository = authRepository,
                onRegisterSuccess = {
                    try {
                        navController.navigate("map") {
                            popUpTo("login") { inclusive = true }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Navigation failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
        composable("map") {
            MapScreen(
                onLoggedOut = {
                    navController.navigate("login") {
                        popUpTo("map") { inclusive = true }
                    }
                },
                imageRepository = imageRepository,
                onOpenLeaderboard = { navController.navigate("leaderboard") }
            )
        }
        composable("leaderboard") {
            LeaderboardScreen(imageRepository = imageRepository, onClose = { navController.popBackStack() })
        }
    }
}