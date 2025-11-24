package com.cloudstaff.myapplication.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cloudstaff.myapplication.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

	override fun onMessageReceived(message: RemoteMessage) {
		println("FCM message received: $message")
		super.onMessageReceived(message)

		// Show notification
		val title = message.notification?.title ?: "Notification"
		val body = message.notification?.body ?: "You have a new message"

		showNotification(title, body)
	}

	private fun showNotification(title: String, body: String) {
		val channelId = "default_channel"
		val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				channelId,
				"Default Channel",
				NotificationManager.IMPORTANCE_HIGH
			)
			manager.createNotificationChannel(channel)
		}

		val notification = NotificationCompat.Builder(this, channelId)
			.setContentTitle(title)
			.setContentText(body)
			.setSmallIcon(R.mipmap.ic_launcher) // your icon
			.setAutoCancel(true)
			.build()

		manager.notify(System.currentTimeMillis().toInt(), notification)
	}

	override fun onNewToken(token: String) {
		super.onNewToken(token)
		// Send token to your server if needed
		println("FCM token: $token")
	}
}
