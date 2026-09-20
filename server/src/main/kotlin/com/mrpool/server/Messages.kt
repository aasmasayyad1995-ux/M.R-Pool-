package com.mrpool.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The envelopes the server and the app exchange.
 *
 * Only the envelope is understood here. A move's contents ride along in `body` as an opaque
 * string, so the server never has to know the rules of pool and never needs redeploying
 * when they change.
 */
object Messages {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ---------------------------------------------------------------- server to client

    fun created(code: String): String = buildJsonObject {
        put("op", "created")
        put("code", code)
    }.toString()

    fun searching(): String = buildJsonObject { put("op", "searching") }.toString()

    fun start(code: String, seat: String, isHost: Boolean, seed: Int, opponent: String): String =
        buildJsonObject {
            put("op", "start")
            put("code", code)
            put("seat", seat)
            put("host", isHost)
            put("seed", seed)
            put("opponent", opponent)
        }.toString()

    /** Wraps a relayed body. The body is passed straight through, never re-encoded. */
    fun peer(body: String): String = buildJsonObject {
        put("op", "peer")
        put("body", parseBody(body))
    }.toString()

    fun gone(): String = buildJsonObject { put("op", "gone") }.toString()

    fun error(reason: String): String = buildJsonObject {
        put("op", "error")
        put("reason", reason)
    }.toString()

    // ---------------------------------------------------------------- client to server

    /** Returns null for anything that is not a readable envelope. */
    fun parse(text: String): Envelope? {
        return try {
            val root = json.parseToJsonElement(text) as? JsonObject ?: return null
            val op = root["op"]?.jsonPrimitive?.contentOrNullSafe() ?: return null
            Envelope(
                op = op,
                code = root["code"]?.jsonPrimitive?.contentOrNullSafe(),
                name = root["name"]?.jsonPrimitive?.contentOrNullSafe(),
                body = root["body"]?.toString()
            )
        } catch (t: Throwable) {
            null
        }
    }

    private fun parseBody(body: String): JsonElement = try {
        json.parseToJsonElement(body)
    } catch (t: Throwable) {
        JsonPrimitive(body)
    }

    private fun JsonPrimitive.contentOrNullSafe(): String = content

    data class Envelope(
        val op: String,
        val code: String?,
        val name: String?,
        val body: String?
    )
}
