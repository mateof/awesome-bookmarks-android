package io.github.mateof.awesomebookmarks.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the account credentials encrypted with an AES-256/GCM key held in the
 * Android Keystore, so the password never touches disk in the clear.
 *
 * The password is kept, rather than only a session cookie, because the server
 * derives the data encryption key from it and drops that key after 30 idle
 * minutes. Without the password the share target would start failing with
 * `423 Locked` the moment the app had been closed for a while.
 *
 * The key is created with `setUserAuthenticationRequired(false)` on purpose:
 * background entry points (the share target, the widget) must authenticate
 * without a foreground unlock. The user-facing gate is the optional biometric
 * app lock, not this key.
 */
@Singleton
class SecretStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readCredentials(): Credentials? {
        val identifier = read(KEY_IDENTIFIER) ?: return null
        val password = read(KEY_PASSWORD) ?: return null
        return Credentials(identifier, password)
    }

    fun writeCredentials(identifier: String, password: String) {
        prefs.edit()
            .putString(KEY_IDENTIFIER, encrypt(identifier))
            .putString(KEY_PASSWORD, encrypt(password))
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun hasCredentials(): Boolean = prefs.contains(KEY_PASSWORD)

    private fun read(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching { decrypt(stored) }
            .onFailure { Log.w(TAG, "Could not decrypt $key, clearing the credentials", it) }
            .getOrElse {
                clear()
                null
            }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val payload = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // iv length | iv | ciphertext, so the IV travels with the payload.
        val combined = ByteArray(1 + iv.size + payload.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(payload, 0, combined, 1 + iv.size, payload.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val ivSize = combined[0].toInt()
        val iv = combined.copyOfRange(1, 1 + ivSize)
        val payload = combined.copyOfRange(1 + ivSize, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(payload), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    data class Credentials(val identifier: String, val password: String)

    private companion object {
        const val TAG = "SecretStore"
        const val PREFS_NAME = "awesome_bookmarks_secrets"
        const val KEY_IDENTIFIER = "account_identifier"
        const val KEY_PASSWORD = "account_password"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "awesome_bookmarks_secret_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
