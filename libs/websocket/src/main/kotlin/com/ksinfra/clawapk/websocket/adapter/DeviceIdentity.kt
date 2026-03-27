package com.ksinfra.clawapk.websocket.adapter

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom

data class DeviceKeys(
    val deviceId: String,
    val publicKey: String,
    val privateKey: String
)

class DeviceIdentity(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("openclaw_device", Context.MODE_PRIVATE)

    fun getOrCreateKeys(): DeviceKeys {
        val existing = loadKeys()
        if (existing != null) return existing

        val keys = generateKeys()
        saveKeys(keys)
        return keys
    }

    fun sign(payload: String): String {
        val keys = getOrCreateKeys()
        val privateKeyBytes = base64UrlDecode(keys.privateKey)
        val privateKey = Ed25519PrivateKeyParameters(privateKeyBytes, 0)

        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        val message = payload.toByteArray(Charsets.UTF_8)
        signer.update(message, 0, message.size)
        val signature = signer.generateSignature()

        return base64UrlEncode(signature)
    }

    private fun generateKeys(): DeviceKeys {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()

        val publicKey = keyPair.public as Ed25519PublicKeyParameters
        val privateKey = keyPair.private as Ed25519PrivateKeyParameters

        val pubBytes = publicKey.encoded
        val privBytes = privateKey.encoded

        val deviceId = sha256Hex(pubBytes)
        val pubBase64 = base64UrlEncode(pubBytes)
        val privBase64 = base64UrlEncode(privBytes)

        return DeviceKeys(deviceId, pubBase64, privBase64)
    }

    private fun loadKeys(): DeviceKeys? {
        val deviceId = prefs.getString("device_id", null) ?: return null
        val pubKey = prefs.getString("public_key", null) ?: return null
        val privKey = prefs.getString("private_key", null) ?: return null

        // Verify deviceId matches publicKey hash
        val actualId = sha256Hex(base64UrlDecode(pubKey))
        if (actualId != deviceId) {
            prefs.edit().clear().apply()
            return null
        }

        return DeviceKeys(deviceId, pubKey, privKey)
    }

    private fun saveKeys(keys: DeviceKeys) {
        prefs.edit()
            .putString("device_id", keys.deviceId)
            .putString("public_key", keys.publicKey)
            .putString("private_key", keys.privateKey)
            .apply()
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun base64UrlDecode(data: String): ByteArray {
        return Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
