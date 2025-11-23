package com.cloudstaff.myapplication.utils.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

object Http {

	val json = Json { ignoreUnknownKeys = true }
	var bearerToken: String? = null

	private fun injectBearer(headers: Map<String, String>): Map<String, String> {
		val token = bearerToken ?: return headers
		return headers + ("Authorization" to "Bearer $token")
	}

	suspend fun request(
		url: String,
		method: String,
		body: String? = null,
		headers: Map<String, String> = emptyMap()
	): String = withContext(Dispatchers.IO) {

		val conn = URL(url).openConnection() as HttpURLConnection
		conn.requestMethod = method
		conn.connectTimeout = 5000
		conn.readTimeout = 5000
		conn.doInput = true

		val finalHeaders = injectBearer(headers)
		finalHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }

		if (body != null) {
			conn.doOutput = true
			conn.setRequestProperty("Content-Type", "application/json")
			conn.outputStream.bufferedWriter().use { it.write(body) }
		}

		val stream = if (conn.responseCode in 200..299)
			conn.inputStream else conn.errorStream
		val response = stream.bufferedReader().use { it.readText() }

		println("⬅️ $method $url")
		body?.let { println(" Body: $it") }
		println("➡️ Response: $response")

		return@withContext response
	}

	suspend fun get(url: String) = request(url, "GET")
	suspend fun delete(url: String) = request(url, "DELETE")
	suspend fun post(url: String, body: String) = request(url, "POST", body)
	suspend fun put(url: String, body: String) = request(url, "PUT", body)

	suspend inline fun <reified T> getJson(url: String): T =
		json.decodeFromString(request(url, "GET"))

	suspend inline fun <reified T> deleteJson(url: String): T =
		json.decodeFromString(request(url, "DELETE"))

	suspend inline fun <reified T> postJson(url: String, body: Any): T =
		json.decodeFromString(
			request(url, "POST", json.encodeToString(body))
		)

	suspend inline fun <reified T> putJson(url: String, body: Any): T =
		json.decodeFromString(
			request(url, "PUT", json.encodeToString(body))
		)
}

