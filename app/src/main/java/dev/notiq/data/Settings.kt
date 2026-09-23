package dev.notiq.data

import android.content.Context
import dev.notiq.R
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore("settings")

object Presets {
    val items = linkedMapOf(
        R.string.preset_conservative to R.string.rule_conservative,
        R.string.preset_transactions to R.string.rule_transactions,
        R.string.preset_quiet to R.string.rule_quiet,
    )
    const val default = "Filter only clear promotions, referral campaigns, coupons and commercial ads. Keep personal messages, payments, orders, deliveries, refunds, support updates, security alerts and service status updates. If unsure whether a notification is an ad, keep it."
}

data class ServiceConfig(val endpoint: String, val model: String, val key: String = "", val threshold: Float = .98f)
data class Settings(
    val revision: Long = 0,
    val observe: Boolean = true,
    val provider: String = "fastjev",
    val prompt: String = Presets.default,
    val theme: String = "system",
    val config: ServiceConfig = ServiceConfig("http://127.0.0.1:8000", "fastjev-local"),
)

class SettingsStore(private val context: Context) {
    private val vault = KeyVault()
    val flow = context.dataStore.data.map { p ->
        val provider = p[stringPreferencesKey("provider")] ?: "fastjev"
        Settings(
            revision = p[longPreferencesKey("revision")] ?: 0,
            observe = p[booleanPreferencesKey("observe")] ?: true,
            provider = provider,
            prompt = p[stringPreferencesKey("prompt")] ?: context.getString(R.string.rule_conservative),
            theme = p[stringPreferencesKey("theme")] ?: "system",
            config = config(p, provider),
        )
    }
    private fun config(p: Preferences, provider: String): ServiceConfig = ServiceConfig(
        p[stringPreferencesKey("${provider}_endpoint")] ?: if (provider == "jev") "https://api.typesafe.ai" else "http://127.0.0.1:8000",
        p[stringPreferencesKey("${provider}_model")] ?: if (provider == "jev") "jev-latest" else "fastjev-local",
        p[stringPreferencesKey("${provider}_key")]?.let { vault.decrypt(it) } ?: "",
        p[floatPreferencesKey("${provider}_threshold")] ?: if (provider == "jev") .98f else .995f,
    )
    private fun bump(p: MutablePreferences) { p[longPreferencesKey("revision")] = (p[longPreferencesKey("revision")] ?: 0) + 1 }
    suspend fun observe(value: Boolean) { context.dataStore.edit { it[booleanPreferencesKey("observe")] = value; bump(it) } }
    suspend fun provider(value: String) { context.dataStore.edit {
        it[stringPreferencesKey("provider")] = value
        it[booleanPreferencesKey("observe")] = true
        bump(it)
    } }
    suspend fun prompt(value: String) { context.dataStore.edit {
        it[stringPreferencesKey("prompt")] = value
        it[booleanPreferencesKey("observe")] = true
        bump(it)
    } }
    suspend fun theme(value: String) { context.dataStore.edit { it[stringPreferencesKey("theme")] = value } }
    suspend fun save(provider: String, value: ServiceConfig) {
        val encrypted = if (value.key.isBlank()) "" else vault.encrypt(value.key)
        context.dataStore.edit {
            it[stringPreferencesKey("${provider}_endpoint")] = value.endpoint.trim().trimEnd('/')
            it[stringPreferencesKey("${provider}_model")] = value.model.trim()
            it[stringPreferencesKey("${provider}_key")] = encrypted
            it[floatPreferencesKey("${provider}_threshold")] = value.threshold
            it[booleanPreferencesKey("observe")] = true
            bump(it)
        }
    }
}

private class KeyVault {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("notiq-api", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("notiq-api", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun decrypt(value: String): String = runCatching {
        if (value.isEmpty()) return ""
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrDefault("")
}
