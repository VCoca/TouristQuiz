package com.example.touristquiz.data

import android.net.Uri
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await

class ObjectRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val imageRepository: ImageRepository
) {
    data class TouristObject(
        val id: String,
        val ownerUid: String,
        val ownerName: String?,
        val name: String,
        val details: String,
        val type: String?,
        val imageUrl: String?,
        val latLng: LatLng,
        val createdAt: Timestamp?
    )

    data class Question(
        val id: String,
        val text: String,
        val options: List<String> = emptyList(),
        val correctAnswer: String? = null,
        val correctIndex: Int? = null,
        val creatorUid: String? = null,
        val ratings: Map<String, Int> = emptyMap(), // userId -> rating (1-5)
        val averageRating: Double = 0.0,
        val numRatings: Int = 0
    )

    data class CreateQuestion(
        val text: String,
        val options: List<String>, // 3
        val correctIndex: Int // 0..2
    )

    fun listenObjects(onChange: (List<TouristObject>) -> Unit): ListenerRegistration {
        return firestore.collection("objects")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { d ->
                    val ownerUid = d.getString("ownerUid") ?: return@mapNotNull null
                    val name = d.getString("name") ?: return@mapNotNull null
                    val details = d.getString("details") ?: ""
                    val imageUrl = d.getString("imageUrl")
                    val lat = d.getDouble("lat") ?: return@mapNotNull null
                    val lng = d.getDouble("lng") ?: return@mapNotNull null
                    val ts = d.getTimestamp("createdAt")
                    val type = d.getString("type")
                    val ownerName = d.getString("ownerName")
                    TouristObject(d.id, ownerUid, ownerName, name, details, type, imageUrl, LatLng(lat, lng), ts)
                }
                onChange(list)
            }
    }

    suspend fun loadQuestions(objectId: String): List<Question> {
        val snap = firestore.collection("objects").document(objectId)
            .collection("questions").get().await()
        return snap.documents.mapNotNull { d ->
            val text = d.getString("text") ?: return@mapNotNull null
            val opt1 = d.getString("option1")
            val opt2 = d.getString("option2")
            val opt3 = d.getString("option3")
            val options = if (opt1 != null && opt2 != null && opt3 != null) listOf(opt1, opt2, opt3) else emptyList()
            val correctIdx = d.getLong("correctIndex")?.toInt()
            val ans = d.getString("correctAnswer")
            val creator = d.getString("creatorUid")
            val ratings = (d.get("ratings") as? Map<*, *>)
                ?.mapNotNull { (k, v) -> (k as? String)?.let { key -> (v as? Number)?.toInt()?.let { key to it } } }
                ?.toMap() ?: emptyMap()
            val avg = (d.getDouble("averageRating") ?: 0.0)
            val num = (d.getLong("numRatings") ?: 0L).toInt()
            Question(
                id = d.id,
                text = text,
                options = options,
                correctAnswer = ans,
                correctIndex = correctIdx,
                creatorUid = creator,
                ratings = ratings,
                averageRating = avg,
                numRatings = num
            )
        }
    }

    private suspend fun resolveOwnerName(uid: String): String? {
        return try {
            val u = firestore.collection("users").document(uid).get().await()
            u.getString("username") ?: u.getString("email") ?: uid
        } catch (_: Exception) { uid }
    }

    private suspend fun uploadImageIfAny(uri: Uri?): String? {
        if (uri == null) return null
        val def = CompletableDeferred<String?>()
        imageRepository.uploadImage(uri) { url ->
            def.complete(url)
        }
        return def.await()
    }

    suspend fun createObject(
        ownerUid: String,
        name: String,
        details: String,
        imageUri: Uri?,
        latLng: LatLng,
        questions: List<CreateQuestion>,
        type: String
    ): String {
        val imageUrl = uploadImageIfAny(imageUri)
        val docRef = firestore.collection("objects").document()
        val ownerName = resolveOwnerName(ownerUid)
        val payload = hashMapOf(
            "ownerUid" to ownerUid,
            "ownerName" to ownerName,
            "name" to name,
            "details" to details,
            "type" to type,
            "imageUrl" to imageUrl,
            "lat" to latLng.latitude,
            "lng" to latLng.longitude,
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        if (questions.isNotEmpty()) {
            val batch = firestore.batch()
            questions.forEach { q ->
                val qRef = docRef.collection("questions").document()
                val safeOptions = if (q.options.size >= 3) q.options.take(3) else listOf("", "", "")
                val correct = safeOptions.getOrNull(q.correctIndex)?.trim()
                batch.set(
                    qRef,
                    mapOf(
                        "text" to q.text,
                        "option1" to safeOptions.getOrNull(0),
                        "option2" to safeOptions.getOrNull(1),
                        "option3" to safeOptions.getOrNull(2),
                        "correctIndex" to q.correctIndex,
                        "correctAnswer" to correct,
                        "creatorUid" to ownerUid,
                        "ratings" to hashMapOf<String, Int>(),
                        "averageRating" to 0.0,
                        "numRatings" to 0
                    )
                )
            }
            batch.commit().await()
        }
        // Korisnik dobija 20 poena za kreiranje objekta
        firestore.collection("users").document(ownerUid)
            .update("points", FieldValue.increment(20)).addOnFailureListener {
                Log.w("ObjectRepository", "Failed to increment create points: ${it.message}")
            }
        return docRef.id
    }

    sealed class AnswerResult {
        data object AlreadyAnswered: AnswerResult()
        data object Correct: AnswerResult()
        data object Incorrect: AnswerResult()
    }

    suspend fun submitAnswerByIndex(
        objectId: String,
        questionId: String,
        userUid: String,
        selectedIndex: Int
    ): AnswerResult {
        val key = "${objectId}_${questionId}"
        val answeredRef = firestore.collection("users").document(userUid)
            .collection("answered").document(key)
        val prev = answeredRef.get().await()
        if (prev.exists()) return AnswerResult.AlreadyAnswered
        val qDoc = firestore.collection("objects").document(objectId)
            .collection("questions").document(questionId).get().await()
        var correctIndex = qDoc.getLong("correctIndex")?.toInt()
        if (correctIndex == null) {
            // Fallback for legacy docs: derive from correctAnswer and options
            val ans = qDoc.getString("correctAnswer")
            val opt1 = qDoc.getString("option1")
            val opt2 = qDoc.getString("option2")
            val opt3 = qDoc.getString("option3")
            val options = listOfNotNull(opt1, opt2, opt3)
            if (ans != null && options.size == 3) {
                val idx = options.indexOfFirst { it.trim().equals(ans.trim(), ignoreCase = true) }
                if (idx in 0..2) correctIndex = idx
            }
        }
        val isCorrect = correctIndex != null && correctIndex == selectedIndex
        if (isCorrect) {
            val batch = firestore.batch()
            batch.set(answeredRef, mapOf("at" to FieldValue.serverTimestamp()))
            val userRef = firestore.collection("users").document(userUid)
            batch.update(userRef, mapOf("points" to FieldValue.increment(5)))
            batch.commit().await()
            return AnswerResult.Correct
        }
        return AnswerResult.Incorrect
    }

    suspend fun getAnsweredQuestionIds(userUid: String, objectId: String): Set<String> {
        return try {
            val snap = firestore.collection("users").document(userUid)
                .collection("answered").get().await()
            snap.documents.map { it.id }
                .filter { it.startsWith("${objectId}_") }
                .map { it.removePrefix("${objectId}_") }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun listenLeaderboard(limit: Long = 50, onChange: (List<Pair<String, Long>>) -> Unit): ListenerRegistration {
        return firestore.collection("users")
            .orderBy("points", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val list = snap.documents.map { d ->
                    val name = d.getString("username") ?: (d.getString("email") ?: d.id)
                    val points = (d.getLong("points") ?: 0L)
                    name to points
                }
                onChange(list)
            }
    }

    fun addQuestionToObject(objectId: String, question: CreateQuestion, creatorUid: String, onComplete: (Boolean) -> Unit) {
        val qRef = firestore.collection("objects").document(objectId).collection("questions").document()
        val safeOptions = if (question.options.size >= 3) question.options.take(3) else listOf("", "", "")
        val correct = safeOptions.getOrNull(question.correctIndex)?.trim()
        val qData = hashMapOf(
            "text" to question.text,
            "option1" to safeOptions.getOrNull(0),
            "option2" to safeOptions.getOrNull(1),
            "option3" to safeOptions.getOrNull(2),
            "correctIndex" to question.correctIndex,
            "correctAnswer" to correct,
            "creatorUid" to creatorUid,
            "ratings" to hashMapOf<String, Int>(),
            "averageRating" to 0.0,
            "numRatings" to 0
        )
        qRef.set(qData)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun rateQuestion(objectId: String, questionId: String, raterUid: String, rating: Int, onComplete: (Boolean) -> Unit) {
        val qRef = firestore.collection("objects").document(objectId).collection("questions").document(questionId)
        qRef.get().addOnSuccessListener { doc ->
            if (!doc.exists()) return@addOnSuccessListener onComplete(false)
            val ratingsAny = doc.get("ratings")
            val ratings = (ratingsAny as? Map<*, *>)?.mapNotNull { (k, v) ->
                (k as? String)?.let { key -> (v as? Number)?.toInt()?.let { key to it } }
            }?.toMap()?.toMutableMap() ?: hashMapOf()
            ratings[raterUid] = rating
            val intRatings = ratings.values.map { it }
            val avg = if (intRatings.isNotEmpty()) intRatings.average() else 0.0
            val num = intRatings.size
            qRef.update(
                mapOf(
                    "ratings" to ratings,
                    "averageRating" to avg,
                    "numRatings" to num
                )
            ).addOnSuccessListener {
                val creatorUid = doc.getString("creatorUid")
                if (creatorUid != null) {
                    firestore.collection("users").document(creatorUid)
                        .update("points", FieldValue.increment(rating.toLong()))
                }
                firestore.collection("users").document(raterUid)
                    .update("points", FieldValue.increment(1L))
                onComplete(true)
            }.addOnFailureListener { onComplete(false) }
        }.addOnFailureListener { onComplete(false) }
    }

    suspend fun deleteObject(objectId: String, requesterUid: String): Boolean {
        return try {
            val docRef = firestore.collection("objects").document(objectId)
            val snap = docRef.get().await()
            if (!snap.exists()) return false
            val ownerUid = snap.getString("ownerUid")
            if (ownerUid == null || ownerUid != requesterUid) return false
            // Delete questions in chunks to respect batch limits
            val qSnap = docRef.collection("questions").get().await()
            val qDocs = qSnap.documents
            val chunkSize = 400 // below Firestore 500 limit for safety
            qDocs.chunked(chunkSize).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { d -> batch.delete(d.reference) }
                batch.commit().await()
            }
            docRef.delete().await()
            true
        } catch (e: Exception) {
            Log.w("ObjectRepository", "deleteObject failed: ${e.message}")
            false
        }
    }
}
