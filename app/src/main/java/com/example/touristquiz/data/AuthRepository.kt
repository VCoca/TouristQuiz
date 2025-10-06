package com.example.touristquiz.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AuthRepository(private val imageRepository: ImageRepository) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    fun registerUser(
        email: String,
        password: String,
        username: String,
        firstName: String,
        lastName: String,
        phone: String,
        imageUri: Uri?,
        onResult: (Boolean, String?) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: return@addOnCompleteListener

                    if (imageUri != null) {
                        imageRepository.uploadImage(imageUri) { imageUrl ->
                            if (imageUrl != null) {
                                saveUserData(uid, username, firstName, lastName, phone, email, imageUrl)
                                onResult(true, null)
                            } else {
                                onResult(false, "Image upload failed")
                            }
                        }
                    } else {
                        saveUserData(uid, username, firstName, lastName, phone, email, null)
                        onResult(true, null)
                    }
                } else {
                    onResult(false, task.exception?.message)
                }
            }
    }

    private fun saveUserData(
        uid: String,
        username: String,
        firstName: String,
        lastName: String,
        phone: String,
        email: String,
        imageUrl: String?
    ) {
        val user = hashMapOf(
            "username" to username,
            "firstName" to firstName,
            "lastName" to lastName,
            "phone" to phone,
            "email" to email,
            "profileImageUrl" to imageUrl,
            "points" to 0L
        )

        firestore.collection("users").document(uid).set(user)
    }

    fun loginUser(email: String, password: String, onResult: (Boolean, String?) -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            onResult(false, "Please enter both email and password")
            return
        }

        try {
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onResult(true, null)
                    } else {
                        onResult(false, task.exception?.message)
                    }
                }
        } catch (e: Exception) {
            onResult(false, e.message)
        }
    }
}