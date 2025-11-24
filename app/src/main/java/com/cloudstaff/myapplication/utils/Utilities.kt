package com.cloudstaff.myapplication.utils

import android.app.Activity
import android.content.res.Resources
import android.widget.Toast

fun Activity.toast(
	message: String,
	duration: Int = Toast.LENGTH_LONG
) {
	println("[TOAST] $message")
	Toast.makeText(this, message, duration).show()
}

fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()
