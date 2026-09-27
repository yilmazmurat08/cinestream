package com.example.util

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

object InAppReviewManager {
    private const val TAG = "InAppReviewManager"

    fun requestInAppReview(activity: Activity, onComplete: (() -> Unit)? = null) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    val flow = manager.launchReviewFlow(activity, reviewInfo)
                    flow.addOnCompleteListener { _ ->
                        Log.d(TAG, "In-App Review flow finished.")
                        onComplete?.invoke()
                    }
                } else {
                    Log.w(TAG, "Failed to request review flow: ${task.exception?.message}")
                    onComplete?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in requestInAppReview: ${e.message}", e)
            onComplete?.invoke()
        }
    }
}
