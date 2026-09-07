package app.tauri.keystore

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import org.json.JSONObject
import java.nio.charset.Charset
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val SHARED_PREFERENCES_NAME = "secure_storage"
private const val GCM_TAG_LENGTH_BITS = 128

private const val DEFAULT_PROMPT_TITLE = "Authentication required"
private const val DEFAULT_CANCEL_LABEL = "Cancel"

@InvokeArg
class Prompt {
    var title: String? = null
    var subtitle: String? = null
    var cancelLabel: String? = null
    var reason: String? = null
}

@InvokeArg
class StoreRequest {
    lateinit var key: String
    lateinit var value: String
    var prompt: Prompt? = null
}

@InvokeArg
class RetrieveRequest {
    lateinit var key: String
    var prompt: Prompt? = null
}

@InvokeArg
class RemoveRequest {
    lateinit var key: String
}

@TauriPlugin
class KeystorePlugin(private val activity: Activity) : Plugin(activity) {

    @Command
    fun store(invoke: Invoke) {
        val args = invoke.parseArgs(StoreRequest::class.java)

        activity.runOnUiThread {
            val cipher = try {
                generateKeyIfAbsent(args.key)
                encryptionCipher(args.key)
            } catch (e: KeyPermanentlyInvalidatedException) {
                // The enrolled biometrics changed, so the existing key can no
                // longer be used. Nothing was encrypted with it that we cannot
                // discard, so replace it and carry on.
                try {
                    deleteKey(args.key)
                    clearCipherData(args.key)
                    generateKeyIfAbsent(args.key)
                    encryptionCipher(args.key)
                } catch (e: Exception) {
                    invoke.reject("Could not prepare key: ${e.message}")
                    return@runOnUiThread
                }
            } catch (e: Exception) {
                invoke.reject("Could not prepare key: ${e.message}")
                return@runOnUiThread
            }

            authenticate(invoke, args.prompt, cipher) { authenticated ->
                try {
                    val ciphertext =
                        authenticated.doFinal(args.value.toByteArray(Charset.forName("UTF-8")))
                    writeCipherData(args.key, authenticated.iv, ciphertext)
                    invoke.resolve()
                } catch (e: Exception) {
                    invoke.reject("Encryption failed: ${e.message}")
                }
            }
        }
    }

    @Command
    fun retrieve(invoke: Invoke) {
        val args = invoke.parseArgs(RetrieveRequest::class.java)

        val cipherData = readCipherData(args.key)
        if (cipherData == null) {
            // Nothing stored under this key is not an error. `JSONObject.NULL`
            // emits an explicit JSON null; a plain `null` would drop the key.
            invoke.resolve(JSObject().apply { put("value", JSONObject.NULL) })
            return
        }
        val (iv, ciphertext) = cipherData

        activity.runOnUiThread {
            val cipher = try {
                decryptionCipher(args.key, iv)
            } catch (e: KeyPermanentlyInvalidatedException) {
                invoke.reject(
                    "The key for '${args.key}' was invalidated by a change to the enrolled biometrics"
                )
                return@runOnUiThread
            } catch (e: Exception) {
                invoke.reject("Could not prepare key: ${e.message}")
                return@runOnUiThread
            }

            authenticate(invoke, args.prompt, cipher) { authenticated ->
                try {
                    val cleartext = String(authenticated.doFinal(ciphertext), Charset.forName("UTF-8"))
                    invoke.resolve(JSObject().apply { put("value", cleartext) })
                } catch (e: Exception) {
                    invoke.reject("Decryption failed: ${e.message}")
                }
            }
        }
    }

    @Command
    fun remove(invoke: Invoke) {
        val args = invoke.parseArgs(RemoveRequest::class.java)
        try {
            deleteKey(args.key)
            clearCipherData(args.key)
            invoke.resolve()
        } catch (e: Exception) {
            invoke.reject("Could not remove '${args.key}': ${e.localizedMessage}")
        }
    }

    /**
     * Shows the biometric prompt and hands [onAuthenticated] the unlocked cipher.
     *
     * Must be called on the UI thread.
     */
    private fun authenticate(
        invoke: Invoke,
        prompt: Prompt?,
        cipher: Cipher,
        onAuthenticated: (Cipher) -> Unit
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val authenticated = result.cryptoObject?.cipher
                if (authenticated == null) {
                    invoke.reject("No cipher available after authentication")
                    return
                }
                onAuthenticated(authenticated)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                invoke.reject("Authentication error: $errString", errorCode.toString())
            }

            // Deliberately not overridden: a single unrecognised attempt leaves the
            // prompt open for the user to retry, so it must not settle the invoke.
            // `onAuthenticationError` fires once the attempt is finally abandoned.
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(prompt?.title ?: DEFAULT_PROMPT_TITLE)
            .setNegativeButtonText(prompt?.cancelLabel ?: DEFAULT_CANCEL_LABEL)
            .apply { prompt?.subtitle?.let { setSubtitle(it) } }
            .build()

        BiometricPrompt(
            activity as FragmentActivity,
            ContextCompat.getMainExecutor(activity),
            callback
        ).authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun generateKeyIfAbsent(key: String) {
        if (keyStore().containsAlias(key)) return

        val spec = KeyGenParameterSpec.Builder(
            key,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .apply {
                // Require authentication for every single use.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()

        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun deleteKey(key: String) = keyStore().deleteEntry(key)

    private fun secretKey(key: String): SecretKey = keyStore().getKey(key, null) as SecretKey

    private fun encryptionCipher(key: String): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey(key)) }

    private fun decryptionCipher(key: String, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secretKey(key), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }

    private fun preferences(): SharedPreferences =
        activity.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)

    // The ciphertext lives in SharedPreferences alongside the key that encrypted
    // it, so both entries are namespaced by the caller's key.
    private fun ivPref(key: String) = "$key.iv"

    private fun ciphertextPref(key: String) = "$key.ciphertext"

    private fun writeCipherData(key: String, iv: ByteArray, ciphertext: ByteArray) {
        preferences().edit()
            .putString(ivPref(key), Base64.encodeToString(iv, Base64.DEFAULT))
            .putString(ciphertextPref(key), Base64.encodeToString(ciphertext, Base64.DEFAULT))
            .apply()
    }

    private fun readCipherData(key: String): Pair<ByteArray, ByteArray>? {
        val prefs = preferences()
        val iv = prefs.getString(ivPref(key), null) ?: return null
        val ciphertext = prefs.getString(ciphertextPref(key), null) ?: return null
        return Pair(Base64.decode(iv, Base64.DEFAULT), Base64.decode(ciphertext, Base64.DEFAULT))
    }

    private fun clearCipherData(key: String) {
        preferences().edit().remove(ivPref(key)).remove(ciphertextPref(key)).apply()
    }
}
