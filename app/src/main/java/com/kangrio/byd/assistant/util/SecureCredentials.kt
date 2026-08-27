package com.kangrio.byd.assistant.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kangrio.byd.assistant.llm.LlmProviders

/**
 * Stores LLM API keys separately from [Preferences] so a Keystore failure on a
 * customized OEM build degrades to "please re-enter your key" instead of risking
 * the plain SharedPreferences store used everywhere else in the app.
 */
object SecureCredentials {
    private const val TAG = "SecureCredentials"
    private const val FILE_NAME = "secure_credentials"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize encrypted storage, API keys will need to be re-entered", e)
            null
        }
    }

    fun getApiKey(providerId: String): String? = prefs?.getString(keyFor(providerId), null)

    fun setApiKey(providerId: String, key: String) {
        prefs?.edit { putString(keyFor(providerId), key) }
    }

    fun clearApiKey(providerId: String) {
        prefs?.edit { remove(keyFor(providerId)) }
    }

    fun hasAnyKeyConfigured(): Boolean = LlmProviders.all.any { !getApiKey(it.id).isNullOrBlank() }

    private fun keyFor(providerId: String) = "api_key_$providerId"
}
