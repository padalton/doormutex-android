package net.padalton.doormutex.android.data

import java.net.URI
import java.net.URISyntaxException

/**
 * Checks a hub base URL as the user typed it.
 *
 * HTTPS is the default. Plain HTTP is accepted only for addresses that cannot
 * leave the home network (10/8, 172.16/12, 192.168/16, *.local, loopback), and the UI
 * has to warn before it is saved. The manifest allows cleartext traffic in
 * general because Android's network security config cannot express IP
 * ranges; this check is where the rule is enforced.
 */
sealed interface HubUrl {
    data class Ok(
        val url: String,
        val cleartext: Boolean,
    ) : HubUrl

    data object Invalid : HubUrl

    data object CleartextNotPrivate : HubUrl

    companion object {
        fun parse(input: String): HubUrl {
            var s = input.trim()
            if (s.isEmpty()) return Invalid
            if (!s.contains("://")) s = "https://$s"
            s = s.trimEnd('/')
            val uri =
                try {
                    URI(s)
                } catch (_: URISyntaxException) {
                    return Invalid
                }
            val host = uri.host?.lowercase() ?: return Invalid
            if (uri.query != null || uri.fragment != null || uri.userInfo != null) return Invalid
            return when (uri.scheme?.lowercase()) {
                "https" -> Ok(s, cleartext = false)
                "http" -> if (isPrivate(host)) Ok(s, cleartext = true) else CleartextNotPrivate
                else -> Invalid
            }
        }

        fun isPrivate(host: String): Boolean {
            if (host.endsWith(".local")) return true
            // Loopback never leaves the phone; used with `adb reverse` during development.
            if (host == "localhost") return true
            val octets =
                host
                    .split('.')
                    .takeIf { it.size == 4 }
                    ?.map { it.toIntOrNull() ?: return false }
                    ?: return false
            if (octets.any { it !in 0..255 }) return false
            val (a, b) = octets
            return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || a == 127
        }
    }
}
