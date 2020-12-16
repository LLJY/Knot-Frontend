package com.lucas.knot

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import services.NotificationGrpc
import services.NotificationOuterClass
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(private val notificationStub: NotificationGrpc.NotificationBlockingStub) {
    /**
     * Update the notification id in the backend
     */
    suspend fun updateNotificationId(userId: String, token: String) = withContext(Dispatchers.IO) {
        try {
            val request = NotificationOuterClass.UpdateUserTokenRequest.newBuilder()
                    .setUserid(userId)
                    .setToken(token)
                    .build()

            val response = notificationStub.updateUserToken(request)
            Pair<Boolean, String>(response.isSuccessful, response.errorMessage)
        } catch (ex: Exception) {
            Log.e("Update Notification Id", ex.toString())
            Pair<Boolean, String>(false, "Request error")
        }
    }
}