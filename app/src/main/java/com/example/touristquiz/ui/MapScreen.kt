package com.example.touristquiz.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.touristquiz.data.ImageRepository
import com.example.touristquiz.data.ObjectRepository
import com.example.touristquiz.ui.map.components.AddObjectDialog
import com.example.touristquiz.ui.map.components.FiltersDialog
import com.example.touristquiz.ui.map.components.ObjectDetailsDialog
import com.example.touristquiz.ui.map.components.ObjectsTableDialog
import com.example.touristquiz.ui.map.model.QItem
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import com.google.android.gms.maps.CameraUpdateFactory

// Helper: Load questions for an object
private suspend fun repoLoadQuestions(repo: ObjectRepository, objectId: String) =
    repo.loadQuestions(objectId)

@Composable
fun MapScreen(
    onLoggedOut: () -> Unit = {},
    imageRepository: ImageRepository? = null,
    onOpenLeaderboard: () -> Unit = {}
) {
    // --- State: UI and Snackbar ---
    val snackbar = remember { SnackbarHostState() }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // --- State: Location (updated by UserLocationsOverlay) ---
    val cameraPositionState = rememberCameraPositionState()
    var currentLatLng by remember { mutableStateOf<LatLng?>(null) }
    var cameraAnimated by remember { mutableStateOf(false) }

    // --- State: User ---
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUserUid by remember { mutableStateOf(auth.currentUser?.uid) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { fa ->
            currentUserUid = fa.currentUser?.uid
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    // --- State: Repositories ---
    val objectRepo: ObjectRepository? = remember(imageRepository) {
        imageRepository?.let { ObjectRepository(FirebaseFirestore.getInstance(), it) }
    }

    // --- State: Objects and Selection ---
    var objects by remember { mutableStateOf<List<ObjectRepository.TouristObject>>(emptyList()) }
    var selectedObject by remember { mutableStateOf<ObjectRepository.TouristObject?>(null) }
    DisposableEffect(objectRepo) {
        val reg = objectRepo?.listenObjects { list -> objects = list }
        onDispose { reg?.remove() }
    }

    // --- State: Add Object Dialog ---
    var showAddDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newDetails by remember { mutableStateOf("") }
    var newImageUri by remember { mutableStateOf<Uri?>(null) }
    val questions = remember { mutableStateListOf<QItem>() }

    // --- State: Type Options ---
    data class TypeOption(val key: String, val label: String)
    val typeOptions = remember {
        listOf(
            TypeOption("attraction", "Turistička atrakcija"),
            TypeOption("cultural", "Kulturni objekat"),
            TypeOption("historical", "Istorijska lokacija")
        )
    }
    var selectedType by remember { mutableStateOf(typeOptions.first().label) }

    // --- State: Questions ---
    var questionList by remember { mutableStateOf<List<ObjectRepository.Question>>(emptyList()) }
    var answeredIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(selectedObject?.id, currentUserUid) {
        val s = selectedObject ?: return@LaunchedEffect
        questionList = emptyList()
        answeredIds = emptySet()
        runCatching {
            questionList = objectRepo?.let { repoLoadQuestions(it, s.id) } ?: emptyList()
            if (currentUserUid != null && objectRepo != null) {
                answeredIds = objectRepo.getAnsweredQuestionIds(currentUserUid!!, s.id)
            }
        }
    }

    // --- State: Filters ---
    var showFilters by remember { mutableStateOf(false) }
    var onlyMine by remember { mutableStateOf(false) }
    val typeEnabled = remember {
        mutableStateMapOf<String, Boolean>().apply {
            typeOptions.forEach { this[it.key] = true }
        }
    }
    var creatorFilter by remember { mutableStateOf("") }
    var limitByDistance by remember { mutableStateOf(false) }
    var maxDistanceMeters by remember { mutableStateOf(1000f) } // 1km default
    var showObjectsTable by remember { mutableStateOf(false) }

    // --- Helper: Normalize type string to canonical key ---
    fun normalizeType(raw: String?): String {
        val r = raw?.trim()?.lowercase() ?: return "attraction"
        return when {
            r in listOf("turistička atrakcija", "turisticka atrakcija", "turistička lokacija", "turisticka lokacija", "tourist attraction", "attraction") -> "attraction"
            r in listOf("kulturni objekat", "cultural", "culture", "kulturno", "kulturna lokacija") -> "cultural"
            r in listOf("istorijska lokacija", "historical", "history", "istorijski objekat") -> "historical"
            else -> "attraction"
        }
    }

    // --- Compute filtered objects ---
    val myUid = FirebaseAuth.getInstance().currentUser?.uid
    val typeEnabledSnapshot = typeEnabled.toMap()
    val displayedObjects = remember(objects, onlyMine, typeEnabledSnapshot, limitByDistance, maxDistanceMeters, currentLatLng, creatorFilter) {
        val cf = creatorFilter.trim().lowercase()
        objects.filter { obj ->
            val typeKey = normalizeType(obj.type)
            val typeOk = typeEnabled[typeKey] == true
            val mineOk = !onlyMine || (obj.ownerUid == myUid)
            val creatorOk = cf.isEmpty() || (obj.ownerName?.lowercase()?.contains(cf) == true)
            val distOk = if (!limitByDistance || currentLatLng == null) true else {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    currentLatLng!!.latitude, currentLatLng!!.longitude,
                    obj.latLng.latitude, obj.latLng.longitude, results
                )
                results[0] <= maxDistanceMeters
            }
            typeOk && mineOk && creatorOk && distOk
        }
    }

    // --- Animate Camera on First Location ---
    LaunchedEffect(currentLatLng) {
        if (!cameraAnimated && currentLatLng != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(currentLatLng!!, 16f)
            )
            cameraAnimated = true
        }
    }

    // --- BackHandler: Logout on back press ---
    BackHandler(enabled = true) {
        try {
            FirebaseAuth.getInstance().signOut()
        } finally {
            onLoggedOut()
        }
    }

    // --- UI ---
    if (errorMsg == null) {
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.matchParentSize(),
                cameraPositionState = cameraPositionState
            ) {
                // All location markers are handled by UserLocationsOverlay
                UserLocationsOverlay(
                    currentUserId = FirebaseAuth.getInstance().currentUser?.uid,
                    onMyLocationUpdated = { ll -> currentLatLng = ll },
                    showCurrentUserMarker = true
                )
                ObjectMapOverlay(objects = displayedObjects, onObjectClick = { selectedObject = it })
            }

            // --- Top-right: Filters and Objects Table ---
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 16.dp),
                shape = MaterialTheme.shapes.small,
                color = Color(0x88000000)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { showFilters = true }, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Filteri")
                    }
                    Button(onClick = { showObjectsTable = true }, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Objekti")
                    }
                }
            }

            // --- Leaderboard FAB ---
            FloatingActionButton(
                onClick = onOpenLeaderboard,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) { Text("🏆") }

            // --- Add Object FAB ---
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 88.dp)
            ) {
                Text("Dodaj objekat")
            }

            // --- Logout button ---
            Button(
                onClick = {
                    try {
                        FirebaseAuth.getInstance().signOut()
                    } finally {
                        onLoggedOut()
                    }
                },
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            ) {
                Text("Izloguj se")
            }

            // --- Filters dialog ---
            FiltersDialog(
                visible = showFilters,
                onlyMine = onlyMine,
                onOnlyMineChange = { onlyMine = it },
                creatorFilter = creatorFilter,
                onCreatorFilterChange = { creatorFilter = it },
                typeEnabled = typeEnabled,
                onTypeEnabledChange = { t, v -> typeEnabled[t] = v },
                limitByDistance = limitByDistance,
                onLimitByDistanceChange = { limitByDistance = it },
                maxDistanceMeters = maxDistanceMeters,
                onMaxDistanceMetersChange = { maxDistanceMeters = it },
                onDismiss = { showFilters = false }
            )

            // --- Add object dialog ---
            AddObjectDialog(
                visible = showAddDialog,
                newName = newName,
                onNewNameChange = { newName = it },
                newDetails = newDetails,
                onNewDetailsChange = { newDetails = it },
                newImageUri = newImageUri,
                onPickImage = { uri -> newImageUri = uri },
                questions = questions,
                onChangeQuestionText = { idx, text ->
                    val q = questions[idx]
                    questions[idx] = q.copy(text = text)
                },
                onChangeQuestionOption = { idx, optIdx, value ->
                    val q = questions[idx]
                    val newOptions = q.options.toMutableList().apply {
                        while (size < 3) add("")
                        this[optIdx] = value
                    }
                    questions[idx] = q.copy(options = newOptions)
                },
                onChangeQuestionCorrectIndex = { idx, correct ->
                    val q = questions[idx]
                    questions[idx] = q.copy(correctIndex = correct)
                },
                onAddQuestion = { questions.add(QItem("", listOf("", "", ""), 0)) },
                onRemoveQuestion = { idx -> questions.removeAt(idx) },
                selectedType = selectedType,
                onSelectedTypeChange = { selectedType = it },
                onCreate = {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    val loc = currentLatLng
                    if (uid != null && loc != null && objectRepo != null) {
                        val trimmedName = newName.trim()
                        val trimmedDetails = newDetails.trim()
                        val qs = questions
                            .filter { it.text.isNotBlank() && it.options.size >= 3 && it.options.all { op -> op.isNotBlank() } }
                            .map { q -> ObjectRepository.CreateQuestion(q.text.trim(), q.options.map { it.trim() }.take(3), q.correctIndex) }
                        if (trimmedName.isBlank() || trimmedDetails.isBlank() || qs.isEmpty()) {
                            scope.launch { snackbar.showSnackbar("Unesite sve podatke.") }
                            return@AddObjectDialog
                        }
                        val typeKey = when (selectedType.trim().lowercase()) {
                            "turistička atrakcija", "tourist attraction" -> "attraction"
                            "kulturni objekat", "cultural" -> "cultural"
                            "istorijska lokacija", "historical" -> "historical"
                            else -> "attraction"
                        }
                        scope.launch {
                            runCatching {
                                objectRepo.createObject(uid, trimmedName, trimmedDetails, newImageUri, loc, qs, typeKey)
                            }.onSuccess {
                                newName = ""; newDetails = ""; newImageUri = null; questions.clear(); showAddDialog = false
                                snackbar.showSnackbar("Objekat kreiran (+20 poena)")
                            }.onFailure { e -> snackbar.showSnackbar("Neuspeh: ${e.message}") }
                        }
                    }
                },
                onDismiss = { showAddDialog = false }
            )

            // --- Object details dialog ---
            val selectedObj = selectedObject
            ObjectDetailsDialog(
                visible = selectedObj != null,
                name = selectedObj?.name ?: "",
                ownerDisplay = selectedObj?.ownerName ?: (selectedObj?.ownerUid ?: ""),
                ownerUid = selectedObj?.ownerUid,
                type = selectedObj?.type,
                imageUrl = selectedObj?.imageUrl,
                details = selectedObj?.details ?: "",
                questions = questionList,
                currentUserUid = currentUserUid,
                answeredIds = answeredIds,
                onSubmitAnswer = { qId, selectedIndex, onResult ->
                    if (selectedObj != null && currentUserUid != null && objectRepo != null) {
                        scope.launch {
                            val res = objectRepo.submitAnswerByIndex(selectedObj.id, qId, currentUserUid!!, selectedIndex)
                            onResult(res)
                            answeredIds = objectRepo.getAnsweredQuestionIds(currentUserUid!!, selectedObj.id)
                            questionList = objectRepo.loadQuestions(selectedObj.id)
                        }
                    }
                },
                onAddQuestion = { q ->
                    if (selectedObj != null && currentUserUid != null && objectRepo != null) {
                        objectRepo.addQuestionToObject(selectedObj.id, q, currentUserUid!!) { success ->
                            if (success) {
                                scope.launch {
                                    questionList = objectRepo.loadQuestions(selectedObj.id)
                                }
                            }
                        }
                    }
                },
                onRateQuestion = { qId, rating ->
                    if (selectedObj != null && currentUserUid != null && objectRepo != null) {
                        objectRepo.rateQuestion(selectedObj.id, qId, currentUserUid!!, rating) { success ->
                            if (success) {
                                scope.launch {
                                    questionList = objectRepo.loadQuestions(selectedObj.id)
                                }
                            }
                        }
                    }
                },
                onDeleteObject = {
                    val obj = selectedObj ?: return@ObjectDetailsDialog
                    val uid = currentUserUid ?: return@ObjectDetailsDialog
                    if (objectRepo != null) {
                        scope.launch {
                            val ok = runCatching { objectRepo.deleteObject(obj.id, uid) }.getOrElse { false }
                            if (ok) {
                                snackbar.showSnackbar("Objekat obrisan")
                                selectedObject = null
                            } else {
                                snackbar.showSnackbar("Greska pri brisanju")
                            }
                        }
                    }
                },
                onDismiss = { selectedObject = null }
            )

            // --- Objects table dialog ---
            ObjectsTableDialog(
                visible = showObjectsTable,
                objects = displayedObjects,
                onDismiss = { showObjectsTable = false },
                onOpenObject = { obj ->
                    selectedObject = obj
                    showObjectsTable = false
                }
            )

            // --- Snackbar ---
            SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))

            // --- Location loading indicator ---
            if (currentLatLng == null) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    Surface(shadowElevation = 4.dp) {
                        Text(text = "Trazi se lokacija...")
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Greska: $errorMsg")
        }
    }
}
