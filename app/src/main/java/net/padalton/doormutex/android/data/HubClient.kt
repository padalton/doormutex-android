package net.padalton.doormutex.android.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
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
    /** `GET /api/v1/profiles`: every profile the token sees, with its state. */
    suspend fun profiles(hub: HubConfig): List<ProfileInfo> = get(hub, "/api/v1/profiles", ListSerializer(ProfileInfo.serializer()))!!

    /** `GET /api/v1/me`; null on a hub older than API 0.2.0, which has no such route. */
    suspend fun me(hub: HubConfig): Me? = get(hub, "/api/v1/me", Me.serializer(), notFoundIsNull = true)

    private suspend fun <T> get(
        hub: HubConfig,
        path: String,
        serializer: KSerializer<T>,
        notFoundIsNull: Boolean = false,
    ): T? =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request
                        .Builder()
                        .url(hub.url + path)
                        .header("Authorization", "Bearer ${hub.token}")
                        .header("Accept", "application/json")
                        .build()
                http.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    when {
                        response.isSuccessful -> HubJson.decodeFromString(serializer, body)
                        response.code == 404 && notFoundIsNull -> null
                        response.code == 401 -> throw HubException.Unauthorized()
                        response.code == 403 -> throw HubException.Forbidden(errorText(body))
                        else -> throw HubException.Http(response.code, errorText(body))
                    }
                }
            } catch (e: IOException) {
                // Path and exception only: the token is in a header, never in the URL or message.
                Log.i(TAG, "GET $path: ${e.javaClass.simpleName}: ${e.message}")
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

        fun defaultHttpClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
    }
}
