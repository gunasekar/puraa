package com.puraa.telegram

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper over the Telegram Bot API. Holds no state beyond the bot
 * token it was constructed with. Only sends — the relay never reads.
 */
class TelegramClient(
    private val botToken: String,
    private val http: OkHttpClient = defaultHttp,
) {

    /** POSTs a text message to a chat/channel. Returns the message id on success. */
    suspend fun sendMessage(chatId: Long, text: String): Long = withContext(Dispatchers.IO) {
        // Telegram rejects messages over 4096 chars with HTTP 400; cap so a
        // long multipart SMS can't become a permanently-failing outbox row.
        val safeText = if (text.length > MAX_TEXT) text.take(MAX_TEXT - 1) + "…" else text
        val url = "https://api.telegram.org/bot$botToken/sendMessage".toHttpUrl()
        val body = FormBody.Builder()
            .add("chat_id", chatId.toString())
            .add("text", safeText)
            .build()
        val req = Request.Builder().url(url).post(body).build()
        http.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw TelegramApiException("sendMessage HTTP ${resp.code}: $raw")
            }
            val envelope = json.decodeFromString(SendMessageResponse.serializer(), raw)
            if (!envelope.ok || envelope.result == null) {
                throw TelegramApiException("sendMessage returned ok=false: $raw")
            }
            envelope.result.message_id
        }
    }

    class TelegramApiException(message: String, cause: Throwable? = null) :
        IOException(message, cause)

    private companion object {
        const val MAX_TEXT = 4096
        val defaultHttp: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
data class SendMessageResponse(
    val ok: Boolean,
    val result: MessageResult? = null,
)

@Serializable
data class MessageResult(
    val message_id: Long,
)
