package net.padalton.doormutex.android.data

import java.net.URI
import java.net.URLDecoder

/**
 * `doormutex://pair?hub=<url-encoded base URL>&token=<dm_…>&name=<token name>`,
 * shown as a QR code in the hub's web UI (Settings → Add a phone).
 * Nothing is saved from a link without the user confirming the check result.
 */
data class PairLink(
    val hub: String,
    val token: String,
    val name: String?,
) {
    override fun toString() = "PairLink(hub=$hub, token=***, name=$name)"

    companion object {
        const val SCHEME = "doormutex"
        const val HOST = "pair"

        fun parse(text: String): PairLink? {
            val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return null
            if (!uri.scheme.equals(SCHEME, ignoreCase = true) || !uri.host.equals(HOST, ignoreCase = true)) return null
            // Decoded by hand: URI.getQuery() would turn an encoded "&" in the hub URL into a separator.
            val params =
                runCatching {
                    uri.rawQuery
                        .orEmpty()
                        .split('&')
                        .filter { it.isNotEmpty() }
                        .associate {
                            val (k, v) = if ('=' in it) it.substringBefore('=') to it.substringAfter('=') else it to ""
                            decode(k) to decode(v)
                        }
                }.getOrNull() ?: return null // malformed %-encoding
            val hub = params["hub"]?.trim().orEmpty()
            val token = params["token"]?.trim().orEmpty()
            if (hub.isEmpty() || token.isEmpty()) return null
            return PairLink(hub, token, params["name"]?.takeIf { it.isNotBlank() })
        }

        private fun decode(s: String) = URLDecoder.decode(s, "UTF-8") // the Charset overload needs API 33
    }
}
