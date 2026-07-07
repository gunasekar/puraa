package com.puraa.discord

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper over a Discord channel webhook. Posting `{"content": ...}`
 * to the webhook URL delivers a message to that channel — the direct
 * analog of Telegram's `sendMessage`, with no bot or gateway. A
 * successful post returns HTTP 204 (no body).
 */
class DiscordClient(
    private val webhookUrl: String,
    private val http: OkHttpClient = defaultHttp,
) {

    suspend fun send(text: String): Unit = withContext(Dispatchers.IO) {
        // Discord caps content at 2000 chars; SMS fit, but guard anyway.
        val content = if (text.length > MAX_CONTENT) text.take(MAX_CONTENT - 1) + "…" else text
        // Suppress mentions so an SMS body containing "@everyone"/"@here"
        // can't ping the whole server.
        val payload = json.encodeToString(
            DiscordMessage.serializer(),
            DiscordMessage(content = content, allowed_mentions = AllowedMentions()),
        )
        val req = Request.Builder()
            .url(webhookUrl)
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                throw DiscordApiException("webhook HTTP ${resp.code}: $body")
            }
        }
    }

    class DiscordApiException(message: String) : IOException(message)

    private companion object {
        const val MAX_CONTENT = 2000
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val json = Json { encodeDefaults = true }
        val defaultHttp: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

@Serializable
private data class DiscordMessage(
    val content: String,
    val allowed_mentions: AllowedMentions,
)

/** Empty parse list = no user/role/@everyone mentions are resolved. */
@Serializable
private data class AllowedMentions(val parse: List<String> = emptyList())
