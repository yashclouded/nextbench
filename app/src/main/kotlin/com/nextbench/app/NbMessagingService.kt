package com.nextbench.app

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NbMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Token refresh — persisted to Firestore by the auth layer once the user is signed in.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Foreground FCM messages are handled here in P7 (Notifications phase).
    }
}
