package de.heimnetz.monitor.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

fun JsonElement?.path(vararg keys: String): JsonElement? {
    var cur: JsonElement? = this
    for (k in keys) cur = (cur as? JsonObject)?.get(k)
    return cur
}

fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.doubleOrNull
fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
