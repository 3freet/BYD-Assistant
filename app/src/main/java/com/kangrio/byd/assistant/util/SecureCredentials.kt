package com.kangrio.byd.assistant.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
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
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                FILE_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize encrypted storage, API keys will need to be re-entered", e)
            null
        }
    }

    fun getApiKey(providerId: String): String? = prefs?.getString(keyFor(providerId), null)

    /** @return false if encrypted storage never initialized (see [init]) — the key was NOT saved,
     * and the caller must tell the user rather than assuming success. */
    fun setApiKey(providerId: String, key: String): Boolean {
        val store = prefs ?: return false
        store.edit { putString(keyFor(providerId), key) }
        return true
    }

    fun clearApiKey(providerId: String) {
        prefs?.edit { remove(keyFor(providerId)) }
    }

    fun hasAnyKeyConfigured(): Boolean = LlmProviders.all.any { !getApiKey(it.id).isNullOrBlank() }

    private fun keyFor(providerId: String) = "api_key_$providerId"
}
