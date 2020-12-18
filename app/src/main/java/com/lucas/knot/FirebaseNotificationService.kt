package com.lucas.knot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlin.random.Random


class FirebaseNotificationService : FirebaseMessagingService() {
    @Inject
    lateinit var notificationRepository: NotificationRepository

    // we cannot inject as lazy injection causes access from override methods to break (NPE)
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    // create a persistent class so that random retains its entropy
    private val random = Random(3000)
    override fun onMessageReceived(p0: RemoteMessage) {
        super.onMessageReceived(p0)
        // get the notification manager
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "KNOT_MESSAGE_NOTIFICATION_CHANNEL"
        val notificationId = random.nextInt()

        // create a notification channel on oreo and above
        val bitmap = getBitmapFromURL(p0.notification?.imageUrl.toString())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                    channelId,
                    "Received Messages",
                    NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Receive notifications when a message is received"
            notificationManager.createNotificationChannel(channel)
        }
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.alert_dark_frame)
                .setLargeIcon(bitmap)
                .setContentTitle(p0.notification?.title)
                .setContentText(p0.notification?.body)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    override fun onNewToken(p0: String) {
        super.onNewToken(p0)
        if (auth.currentUser != null) {
            GlobalScope.launch(Dispatchers.IO) {
                notificationRepository.updateNotificationId(auth.currentUser!!.uid, p0)
            }
        }
    }
}

/**
 * Gets image bitmap from url, useful for notifications
 */
fun getBitmapFromURL(src: String?): Bitmap? {
    return try {
        val url = URL(src)
        val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
        connection.doInput = true
        connection.connect()
        val input: InputStream = connection.inputStream
        BitmapFactory.decodeStream(input)
    } catch (e: IOException) {
        // Log exception
        null
    }
}