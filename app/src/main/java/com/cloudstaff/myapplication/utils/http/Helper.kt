package com.cloudstaff.myapplication.utils.http

import kotlinx.serialization.json.*

/**
 * Recursively converts a Map<String, Any?> into a JsonObject
 */
fun mapToJsonObject(map: Map<String, Any?>): JsonObject {
	return buildJsonObject {
		map.forEach { (key, value) ->
			when (value) {
				null -> put(key, JsonNull)
				is String -> put(key, value)
				is Number -> put(key, JsonPrimitive(value))
				is Boolean -> put(key, JsonPrimitive(value))
				is Map<*, *> -> {
					// recursive cast Map<*, *> to Map<String, Any?>
					@Suppress("UNCHECKED_CAST")
					put(key, mapToJsonObject(value as Map<String, Any?>))
				}
				is List<*> -> {
					put(key, listToJsonArray(value))
				}
				else -> put(key, JsonPrimitive(value.toString()))
			}
		}
	}
}

/**
 * Recursively converts a List<*> into JsonArray
 */
fun listToJsonArray(list: List<*>): JsonArray {
	return buildJsonArray {
		list.forEach { value ->
			when (value) {
				null -> add(JsonNull)
				is String -> add(value)
				is Number -> add(JsonPrimitive(value))
				is Boolean -> add(JsonPrimitive(value))
				is Map<*, *> -> {
					@Suppress("UNCHECKED_CAST")
					add(mapToJsonObject(value as Map<String, Any?>))
				}
				is List<*> -> add(listToJsonArray(value))
				else -> add(JsonPrimitive(value.toString()))
			}
		}
	}
}
