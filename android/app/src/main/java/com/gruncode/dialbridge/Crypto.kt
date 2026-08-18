package com.gruncode.dialbridge

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The encryption that lets this app use anybody's delivery service without
 * handing them the phone number.
 *
 * The browser encrypts; this decrypts. The key exists only on those two
 * devices — it is created here, shown once in the pairing code, and never
 * transmitted afterwards. Whatever sits in between (a public ntfy server,
 * Firebase Cloud Messaging, the relay) carries ciphertext it cannot read.
 *
 * AES-GCM, 256-bit key, 96-bit random nonce per message, 128-bit tag. The wire
 * format is nonce || ciphertext || tag, which is what Web Crypto produces on
 * the browser side, so the two implementations interoperate without glue.
 */
object Crypto {

    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    private const val B64 = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    /** Create a fresh key. Called once, when the user generates a pairing code. */
    fun newKey(): String {
        val key = ByteArray(KEY_BITS / 8)
        SecureRandom().nextBytes(key)
        return Base64.encodeToString(key, B64)
    }

    /**
     * Decrypt one message.
     *
     * Returns null on any failure rather than throwing. A failure here means
     * the packet was corrupt, replayed from an old pairing, or forged by
     * someone who found the topic — none of which should crash the listener,
     * and all of which should simply be dropped.
     */
    fun decrypt(base64UrlKey: String, payload: String): String? {
        return try {
            val key = Base64.decode(base64UrlKey, B64)
            val packet = Base64.decode(payload.trim(), B64)
            if (packet.size <= NONCE_BYTES) return null

            val nonce = packet.copyOfRange(0, NONCE_BYTES)
            val sealed = packet.copyOfRange(NONCE_BYTES, packet.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, nonce)
            )

            // GCM verifies the tag during doFinal: a forged or altered message
            // throws here rather than decrypting to garbage, which is the whole
            // point of using an authenticated mode.
            String(cipher.doFinal(sealed), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** Encode the pairing details the browser needs, as one copyable string. */
    fun encodePairing(json: String): String =
        Base64.encodeToString(json.toByteArray(Charsets.UTF_8), B64)
}
