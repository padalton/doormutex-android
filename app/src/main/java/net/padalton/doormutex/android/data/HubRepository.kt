package net.padalton.doormutex.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

private val Context.store by preferencesDataStore(name = "hub")

/** What the hub said about a URL and token before they are saved. */
data class PairCheck(
    val config: HubConfig,
    val profiles: List<ProfileInfo>,
    /** Null on a hub older than API 0.2.0 (no `/me`). */
    val me: Me?,
)

/**
 * The single source for the hub connection and the cached state. One hub,
 * any number of profiles (see issue padalton/doormutex#28).
 */
class HubRepository(
    private val context: Context,
    private val client: HubClient = HubClient(),
    private val cipher: TokenCipher = TokenCipher(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val refreshLock = Mutex()

    // Decrypt only when URL or token change, not on every snapshot write.
    val config: Flow<HubConfig?> =
        context.store.data
            .map { it[URL] to it[TOKEN] }
            .distinctUntilChanged()
            .map { (url, blob) -> if (url == null || blob == null) null else cipher.decrypt(blob)?.let { HubConfig(url, it) } }

    val snapshot: Flow<Snapshot> =
        context.store.data
            .map { it[SNAPSHOT]?.let(::decodeSnapshot) ?: Snapshot() }
            .distinctUntilChanged()

    val me: Flow<Me?> =
        context.store.data
            .map { prefs -> prefs[ME]?.let { runCatching { HubJson.decodeFromString(Me.serializer(), it) }.getOrNull() } }
            .distinctUntilChanged()

    /** Asks the hub with [url] and [token] without saving anything. */
    suspend fun check(
        url: String,
        token: String,
    ): PairCheck {
        val config = HubConfig(url, token.trim())
        val profiles = client.profiles(config)
        val me = client.me(config)
        return PairCheck(config, profiles, me)
    }

    suspend fun save(check: PairCheck) {
        val now = clock()
        context.store.edit {
            it.clear()
            it[URL] = check.config.url
            it[TOKEN] = cipher.encrypt(check.config.token)
            check.me?.let { me -> it[ME] = HubJson.encodeToString(Me.serializer(), me) }
            it[SNAPSHOT] = encodeSnapshot(Snapshot(check.profiles, fetchedAt = now, attemptedAt = now))
        }
    }

    suspend fun forget() {
        context.store.edit { it.clear() }
        cipher.deleteKey()
    }

    /**
     * Fetches all profiles and stores them. A failure is recorded in the
     * snapshot's [Snapshot.problem]; the old profiles stay, with their old
     * time. Returns null when no hub is paired.
     */
    suspend fun refresh(): Snapshot? =
        refreshLock.withLock {
            val config = config.first() ?: return null
            val old = snapshot.first()
            val now = clock()
            val next =
                try {
                    val profiles = client.profiles(config)
                    old.copy(profiles = profiles, fetchedAt = now, attemptedAt = now, problem = null)
                } catch (e: HubException) {
                    old.copy(attemptedAt = now, problem = Problem.of(e))
                }
            context.store.edit { it[SNAPSHOT] = encodeSnapshot(next) }
            next
        }

    private fun encodeSnapshot(s: Snapshot) = HubJson.encodeToString(Snapshot.serializer(), s)

    private fun decodeSnapshot(s: String): Snapshot? =
        try {
            HubJson.decodeFromString(Snapshot.serializer(), s)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private companion object {
        val URL = stringPreferencesKey("url")
        val TOKEN = stringPreferencesKey("token")
        val ME = stringPreferencesKey("me")
        val SNAPSHOT = stringPreferencesKey("snapshot")
    }
}
