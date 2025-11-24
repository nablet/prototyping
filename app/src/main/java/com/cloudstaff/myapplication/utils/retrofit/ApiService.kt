package com.cloudstaff.myapplication.utils.retrofit

import com.cloudstaff.myapplication.ui.DifyResponse
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST


import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// Optional: Add bearer token automatically
val token = "app-RUajyaiMG0aoPHM4bWRMpNTW"
val client = OkHttpClient.Builder()
	.addInterceptor { chain: Interceptor.Chain ->
		val request = chain.request().newBuilder()
			.addHeader("Authorization", "Bearer $token")
			.build()
		chain.proceed(request)
	}
	.addInterceptor(HttpLoggingInterceptor().apply {
		setLevel(HttpLoggingInterceptor.Level.BODY)
	})
	.connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
	.readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
	.writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
	.build()

val retrofit = Retrofit.Builder()
	.baseUrl("https://dify-hackaton.cloudstaff.io/") // base URL
	.client(client)
	.addConverterFactory(GsonConverterFactory.create())
	.build()

val api = retrofit.create(ApiService::class.java)



interface ApiService {

	@Headers("Content-Type: application/json")
	@POST("v1/workflows/run")
	suspend fun postData(@Body payload: Payload): DifyResponse
}

data class Inputs(
	val type: String,
	val barangay: String,
	val city: String,
	val coordinate: String
)

data class Payload(
	val inputs: Inputs,
	val user: String
)