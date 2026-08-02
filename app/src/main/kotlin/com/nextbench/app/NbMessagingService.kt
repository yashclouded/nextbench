package com.nextbench.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.auth.FirebaseAuth
import com.nextbench.data.firebase.NotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NbMessagingService : FirebaseMessagingService() {

    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var repository: NotificationRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
    }

    override fun onNewToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        serviceScope.launch {
            repository.registerMessagingToken(uid, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "NextBench update"
        val body = message.notification?.body ?: message.data["body"] ?: message.data["message"] ?: "You have a new update."
        val link = message.data["link"].orEmpty()
        val deepLink = link.toAppDeepLink()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (deepLink != null) data = android.net.Uri.parse(deepLink)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            message.data["notificationId"]?.hashCode() ?: message.messageId?.hashCode() ?: body.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(com.nextbench.app.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted && NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            runCatching {
                val notificationId = message.data["notificationId"]?.hashCode()
                    ?: message.messageId?.hashCode()
                    ?: body.hashCode()
                NotificationManagerCompat.from(this).notify(notificationId, notification)
            }
        }
    }

    companion object {
        internal const val ChannelId = "nextbench_updates"
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

internal fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(
            NbMessagingService.ChannelId,
            "NextBench updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Messages, marketplace updates, and account activity"
        },
    )
}

internal fun String.toAppDeepLink(): String? {
    val value = trim()
    if (value.isBlank()) return null
    if (value.startsWith("nextbench://")) return value
    val normalized = value
        .removePrefix("https://nextbench.in/")
        .removePrefix("http://nextbench.in/")
        .removePrefix("/")
    val destination = if (normalized == "dashboard") "community" else normalized
    return destination.takeIf(String::isNotBlank)?.let { "nextbench://$it" }
}
