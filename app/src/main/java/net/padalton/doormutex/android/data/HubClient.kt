package net.padalton.doormutex.android.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class HubConfig(
    val url: String,
    val token: String,
) {
    // Never print the token, not even in a debug log.
    override fun toString() = "HubConfig(url=$url, token=***)"
}

sealed class HubException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** No answer: wrong address, not on the home network, hub down. */
    class Unreachable(
        cause: IOException,
    ) : HubException("hub not reachable", cause)

    /** 401: the token is unknown or was revoked. */
    class Unauthorized : HubException("token rejected")

    /** 403: read-only token or a profile outside the token's list. */
    class Forbidden(
        val reason: String?,
    ) : HubException("forbidden: $reason")

    class Http(
        val code: Int,
        val reason: String?,
    ) : HubException("HTTP $code: $reason")

    /** The hub answered something that is not the documented JSON. */
    class BadResponse(
        cause: Throwable,
    ) : HubException("unexpected response", cause)
}

class HubClient(
    private val http: OkHttpClient = defaultHttpClient(),
) {
    // The hub sends a keep-alive every 25 s; no byte for a minute means the connection is dead.
    private val stream = http.newBuilder().readTimeout(STREAM_READ_TIMEOUT_S, TimeUnit.SECONDS).build()

    /** `GET /api/v1/profiles`: every profile the token sees, with its state. */
    suspend fun profiles(hub: HubConfig): List<ProfileInfo> = get(hub, "/api/v1/profiles", ListSerializer(ProfileInfo.serializer()))!!

    /** `GET /api/v1/me`; null on a hub older than API 0.2.0, which has no such route. */
    suspend fun me(hub: HubConfig): Me? = get(hub, "/api/v1/me", Me.serializer(), notFoundIsNull = true)

    /** `GET /api/v1/timeline`: planned slots of [profile] for the next 24 hours. */
    suspend fun timeline(
        hub: HubConfig,
        profile: String,
    ): Timeline = get(hub, "/api/v1/timeline?profile=${profile.encoded()}", Timeline.serializer())!!

    /** `GET /api/v1/eink.png`: what the e-ink display of [profile] shows, as PNG. */
    suspend fun eink(
        hub: HubConfig,
        profile: String,
    ): ByteArray = exchange(hub, "GET", "/api/v1/eink.png?profile=${profile.encoded()}", null, "image/png") { it.bytes() }!!

    /** `PUT /api/v1/override` (full tokens only): returns the new state. */
    suspend fun setOverride(
        hub: HubConfig,
        profile: String,
        override: OverrideRequest,
    ): State =
        exchange(
            hub,
            "PUT",
            "/api/v1/override?profile=${profile.encoded()}",
            HubJson.encodeToString(OverrideRequest.serializer(), override),
            "application/json",
        ) {
            HubJson.decodeFromString(State.serializer(), it.string())
        }!!

    /** `DELETE /api/v1/override`: back to automatic; returns the new state. */
    suspend fun clearOverride(
        hub: HubConfig,
        profile: String,
    ): State =
        exchange(hub, "DELETE", "/api/v1/override?profile=${profile.encoded()}", null, "application/json") {
            HubJson.decodeFromString(State.serializer(), it.string())
        }!!

    private fun String.encoded() = java.net.URLEncoder.encode(this, "UTF-8") // the Charset overload needs API 33

    /**
     * `GET /api/v1/events?profile=*` (hub API 0.2.0). Completes when the hub
     * ends the stream (after 10 minutes, by design): reconnect then. Throws
     * [HubException] like the other calls; a hub before 0.2.0 answers 404.
     */
    fun events(hub: HubConfig): Flow<StreamEvent> =
        callbackFlow {
            val call = stream.newCall(request(hub, "/api/v1/events?profile=*", "text/event-stream"))
            launch(Dispatchers.IO) {
                try {
                    call.execute().use { response ->
                        check(response, response.isSuccessful) { response.body.string() }
                        val source = response.body.source()
                        val parser = SseParser()
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            when (val raw = parser.line(line)) {
                                SseParser.Raw.Comment -> {
                                    send(StreamEvent.KeepAlive)
                                }

                                is SseParser.Raw.Event -> {
                                    if (raw.type == "state") {
                                        send(StreamEvent.Update(HubJson.decodeFromString(ProfileState.serializer(), raw.data)))
                                    }
                                }

                                null -> {}
                            }
                        }
                    }
                    close()
                } catch (e: IOException) {
                    // Also the way a cancelled call ends; then nobody listens any more.
                    Log.i(TAG, "events: ${e.javaClass.simpleName}: ${e.message}")
                    close(HubException.Unreachable(e))
                } catch (e: HubException) {
                    close(e)
                } catch (e: SerializationException) {
                    close(HubException.BadResponse(e))
                } catch (e: IllegalArgumentException) {
                    close(HubException.BadResponse(e))
                }
            }
            awaitClose { call.cancel() }
        }

    private fun request(
        hub: HubConfig,
        path: String,
        accept: String,
    ) = Request
        .Builder()
        .url(hub.url + path)
        .header("Authorization", "Bearer ${hub.token}")
        .header("Accept", accept)
        .build()

    /** Maps a non-successful response to a [HubException]. */
    private inline fun check(
        response: Response,
        ok: Boolean,
        body: () -> String,
    ) {
        if (ok) return
        when (response.code) {
            401 -> throw HubException.Unauthorized()
            403 -> throw HubException.Forbidden(errorText(body()))
            else -> throw HubException.Http(response.code, errorText(body()))
        }
    }

    private suspend fun <T> get(
        hub: HubConfig,
        path: String,
        serializer: KSerializer<T>,
        notFoundIsNull: Boolean = false,
    ): T? = exchange(hub, "GET", path, null, "application/json", notFoundIsNull) { HubJson.decodeFromString(serializer, it.string()) }

    /**
     * One request. [decode] reads a successful body. Errors become
     * [HubException]s; a 404 is null when [notFoundIsNull].
     */
    private suspend fun <T> exchange(
        hub: HubConfig,
        method: String,
        path: String,
        json: String?,
        accept: String,
        notFoundIsNull: Boolean = false,
        decode: (ResponseBody) -> T,
    ): T? =
        withContext(Dispatchers.IO) {
            try {
                val body = json?.toRequestBody("application/json".toMediaType())
                val request =
                    request(hub, path, accept)
                        .newBuilder()
                        .method(method, body)
                        .build()
                http.newCall(request).execute().use { response ->
                    if (response.code == 404 && notFoundIsNull) return@withContext null
                    check(response, response.isSuccessful) { response.body.string() }
                    decode(response.body)
                }
            } catch (e: IOException) {
                // Path and exception only: the token is in a header, never in the URL or message.
                Log.i(TAG, "$method $path: ${e.javaClass.simpleName}: ${e.message}")
                throw HubException.Unreachable(e)
            } catch (e: SerializationException) {
                throw HubException.BadResponse(e)
            } catch (e: IllegalArgumentException) {
                // Also thrown for a URL OkHttp cannot parse, and by java.time.
                throw HubException.BadResponse(e)
            }
        }

    private fun errorText(body: String): String? =
        try {
            HubJson.decodeFromString(HubErrorBody.serializer(), body).error
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    companion object {
        private const val TAG = "doormutex"
        private const val STREAM_READ_TIMEOUT_S = 60L

        fun defaultHttpClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
    }
}
