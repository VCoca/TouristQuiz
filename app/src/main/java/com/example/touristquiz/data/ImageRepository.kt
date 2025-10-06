package com.example.touristquiz.data

import android.content.Context
import android.net.Uri
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.UploadCallback

class ImageRepository(context: Context) {

    init {
        val config = mapOf(
            "cloud_name" to "dqupgojhu",
            "api_key" to "227476317486783",
            "api_secret" to "iIAkDcHj24Fcgxw0SgqV2A0qwBk"
        )
        MediaManager.init(context, config)
    }

    fun uploadImage(uri: Uri, onResult: (String?) -> Unit) {
        MediaManager.get().upload(uri).callback(object : UploadCallback {
            override fun onStart(requestId: String?) {}

            override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}

            override fun onSuccess(requestId: String?, resultData: Map<*, *>) {
                val url = resultData["secure_url"] as? String
                onResult(url)
            }

            override fun onError(requestId: String?, error: ErrorInfo?) {
                error?.let { println("Error: ${it.description}") }
                onResult(null)
            }

            override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
        }).dispatch()
    }
}