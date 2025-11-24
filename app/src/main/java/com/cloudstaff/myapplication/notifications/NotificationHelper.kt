package com.cloudstaff.myapplication.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cloudstaff.myapplication.R

fun Context.showNotification(title: String, message: String) {
	val channelId = "default_channel"

	// Create Notification Channel (Android 8+)
	val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
		val channel = NotificationChannel(
			channelId,
			"General Notifications",
			NotificationManager.IMPORTANCE_HIGH
		)
		channel.enableLights(true)
		channel.enableVibration(true)
		notificationManager.createNotificationChannel(channel)
	}

	val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

	// Build the notification
	val notification = NotificationCompat.Builder(this, channelId)
		.setSmallIcon(R.mipmap.ic_launcher_round) // your app icon
		.setContentTitle(title)
		.setContentText(message)
		.setSound(soundUri)
		.setPriority(NotificationCompat.PRIORITY_HIGH)
		.build()

	// Show the notification
	notificationManager.notify(0, notification)
}