package app.tauri.keystore

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
//import android.hardware.biometrics.BiometricPrompt
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.Logger
import android.util.Base64
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import app.tauri.plugin.Invoke
import java.util.Enumeration
import javax.crypto.KeyGenerator
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEY_ALIAS = "unime_dev"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val SHARED_PREFERENCES_NAME = "secure_storage"

@InvokeArg
class StoreRequest {
    lateinit var value: String
    // TODO: use this instead?
    // var value: String? = null
}

@InvokeArg
class RetrieveRequest {
    lateinit var service: String
    lateinit var user: String
}

@TauriPlugin
class ExamplePlugin(private val activity: Activity) : Plugin(activity) {
    private val implementation = Example()

    @Command
    fun store(invoke: Invoke) {
        val storeRequest = invoke.parseArgs(StoreRequest::class.java)

        // Generate Key (biometrics-protected)
        generateBiometricProtectedKey()

        // Get cipher for encryption
        val cipher = getEncryptionCipher()

        // Wrap the Cipher in a CryptoObject.
        val cryptoObject = BiometricPrompt.CryptoObject(cipher)

        // Create biometric prompt
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt =
            BiometricPrompt(activity as androidx.fragment.app.FragmentActivity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        try {
                            // Get the cipher from the authentication result.
                            val authCipher = result.cryptoObject?.cipher
                                ?: throw IllegalStateException("Cipher not available after auth")

                            // Encrypt the value.
                            val ciphertext =
                                authCipher.doFinal(storeRequest.value.toByteArray(Charset.forName("UTF-8")))
                            val iv = authCipher.iv  // Capture the initialization vector.

                            // Store the ciphertext and IV.
                            storeCiphertext(iv, ciphertext)
                            Logger.info("Secret stored securely")
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Logger.error("Encryption failed: ${e.message}")
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        invoke.reject("Authentication error: $errorCode")
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        invoke.reject("Authentication failed")
                    }
                })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate to Store Secret")
            .setSubtitle("Biometric authentication is required")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo, cryptoObject)

        // Unlock
//        val spec = javax.crypto.spec.GCMParameterSpec(123, iv)
//        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

//        val biometricPrompt = BiometricPrompt(
//            activity as androidx.fragment.app.FragmentActivity,
//            object : BiometricPrompt.AuthenticationCallback() {
//                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
//                    super.onAuthenticationSucceeded(result)
//                    try {
//                        // Use the cipher from the CryptoObject to decrypt the ciphertext.
//                        val decryptedBytes = result.cryptoObject?.cipher?.doFinal(ciphertext)
//                        val password = decryptedBytes?.toString(StandardCharsets.UTF_8)
//                        if (password != null) {
//                            onDecrypted(password)
//                        } else {
//                            onError("Decryption failed")
//                        }
//                    } catch (e: Exception) {
//                        onError("Decryption exception: ${e.message}")
//                    }
//                }
//                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
//                    super.onAuthenticationError(errorCode, errString)
//                    onError("Authentication error: $errString")
//                }
//                override fun onAuthenticationFailed() {
//                    super.onAuthenticationFailed()
//                    onError("Authentication failed")
//                }
//            }
//        )

        invoke.resolve()
    }

    // Generate key, if it doesn't exist.
    private fun generateBiometricProtectedKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator =
                KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Require authentication on every use:
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(-1)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }

    // Prepares and returns a Cipher instance for encryption using the key from the Keystore.
    private fun getEncryptionCipher(): Cipher {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        // ######## TODO: remove
        val aliases: Enumeration<String> = keyStore.aliases()
        Logger.warn("########## aliases:", aliases.toList().joinToString())
        // ###############

        val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher
    }

    // Stores the IV and ciphertext in SharedPreferences.
    private fun storeCiphertext(iv: ByteArray, ciphertext: ByteArray) {
        val prefs: SharedPreferences =
            activity.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val ivEncoded = Base64.encodeToString(iv, Base64.DEFAULT)
        val ctEncoded = Base64.encodeToString(ciphertext, Base64.DEFAULT)
        editor.putString("iv", ivEncoded)
        editor.putString("ciphertext", ctEncoded)
        editor.apply()
    }

    @Command
    fun retrieve(invoke: Invoke) {
        val args = invoke.parseArgs(RetrieveRequest::class.java)

        val cipherData = readCipherData()
        if (cipherData == null) {
            invoke.reject("No cipher data found in SharedPreferences", "001")
            return
        }

        val (iv, ciphertext) = cipherData

        val cipher = try {
            getDecryptionCipher(iv)
        } catch (e: Exception) {
            invoke.reject("Error initializing cipher: ${e.message}", "001")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity as androidx.fragment.app.FragmentActivity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    try {
                        // Use the cipher from the authentication result (which is now unlocked).
                        val authCipher = result.cryptoObject?.cipher
                            ?: throw IllegalStateException("Cipher not available after authentication")
                        val decryptedBytes = authCipher.doFinal(ciphertext)
                        val cleartext = String(decryptedBytes, Charset.forName("UTF-8"))

                        val ret = JSObject()
                        ret.put("value", cleartext)
                        invoke.resolve(ret)
                    } catch (e: Exception) {
                        invoke.reject("Decryption failed: ${e.message}")
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    invoke.reject("Authentication error: $errorCode")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    invoke.reject("Authentication failed")
                }
            })

        // Build the prompt info.
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Authentication")
            .setSubtitle("Authenticate to decrypt your secret")
            .setNegativeButtonText("Cancel")
            .build()

        // Launch the biometric prompt.
        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }

    // Reads the IV and ciphertext from SharedPreferences.
    private fun readCipherData(): Pair<ByteArray, ByteArray>? {
        val prefs: SharedPreferences =
            activity.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val ivEncoded: String? = prefs.getString("iv", null)
        val ctEncoded: String? = prefs.getString("ciphertext", null)
        if (ivEncoded == null || ctEncoded == null) {
            return null
        }
        val iv = Base64.decode(ivEncoded, Base64.DEFAULT)
        val ciphertext = Base64.decode(ctEncoded, Base64.DEFAULT)
        return Pair(iv, ciphertext)
    }

    private fun getDecryptionCipher(iv: ByteArray): Cipher {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher
    }
}
