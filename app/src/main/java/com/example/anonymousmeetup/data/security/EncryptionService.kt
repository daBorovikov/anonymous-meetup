package com.example.anonymousmeetup.data.security

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import java.util.logging.Level
import java.util.logging.Logger

@Singleton
class EncryptionService @Inject constructor() {
    companion object {
        private const val KEY_SIZE = 256
        private const val PROVIDER = BouncyCastleProvider.PROVIDER_NAME
        private const val ALGORITHM = "EC"
        private const val TRANSFORMATION = "ECIES"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val NONCE_BYTES = 12
        private const val GCM_TAG_LENGTH = 128
        private val logger: Logger = Logger.getLogger(EncryptionService::class.java.name)
    }

    init {
        try { Security.removeProvider(PROVIDER) } catch (_: Exception) { }
        Security.addProvider(BouncyCastleProvider())
        Security.setProperty("crypto.policy", "unlimited")
        logger.fine("EncryptionService initialized with BouncyCastle provider")
    }

    fun generateKeyPair(): KeyPair {
        return try {
            val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM, PROVIDER)
            keyPairGenerator.initialize(KEY_SIZE)
            keyPairGenerator.generateKeyPair()
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Error generating key pair", e)
            throw EncryptionException("Key generation failed: ${e.message}")
        }
    }

    fun exportPublicKey(publicKey: PublicKey): String = Base64.getEncoder().encodeToString(publicKey.encoded)

    fun exportPrivateKey(privateKey: PrivateKey): String = Base64.getEncoder().encodeToString(privateKey.encoded)

    fun importPublicKey(encodedKey: String): PublicKey {
        try {
            require(encodedKey.isNotBlank()) { "Empty key" }
            val keyBytes = Base64.getDecoder().decode(encodedKey)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(ALGORITHM, PROVIDER)
            return keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Error importing public key", e)
            throw EncryptionException("Public key import failed: ${e.message}")
        }
    }

    fun importPrivateKey(encodedKey: String): PrivateKey {
        try {
            require(encodedKey.isNotBlank()) { "Empty key" }
            val keyBytes = Base64.getDecoder().decode(encodedKey)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance(ALGORITHM, PROVIDER)
            return keyFactory.generatePrivate(keySpec)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Error importing private key", e)
            throw EncryptionException("Private key import failed: ${e.message}")
        }
    }

    fun encryptMessage(message: String, recipientPublicKey: String): String {
        try {
            require(message.isNotBlank()) { "Empty message" }
            val publicKey = importPublicKey(recipientPublicKey)
            val cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedBytes = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(encryptedBytes)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Error encrypting message", e)
            throw EncryptionException("Message encryption failed: ${e.message}")
        }
    }

    fun decryptMessage(encryptedMessage: String, privateKey: String): String {
        try {
            require(encryptedMessage.isNotBlank()) { "Empty encrypted message" }
            val key = importPrivateKey(privateKey)
            val cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER)
            cipher.init(Cipher.DECRYPT_MODE, key)
            val encryptedBytes = Base64.getDecoder().decode(encryptedMessage)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Error decrypting message", e)
            throw EncryptionException("Message decryption failed: ${e.message}")
        }
    }

    fun deriveSharedSecret(myPrivateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray {
        try {
            val keyAgreement = KeyAgreement.getInstance("ECDH", PROVIDER)
            keyAgreement.init(myPrivateKey)
            keyAgreement.doPhase(peerPublicKey, true)
            return keyAgreement.generateSecret()
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Error deriving shared secret", e)
            throw EncryptionException("Shared secret derivation failed: ${e.message}")
        }
    }

    fun deriveMessageKey(sharedSecret: ByteArray, nonce: ByteArray): ByteArray {
        return hmacSha256(sharedSecret, nonce).copyOf(32)
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        try {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
            return mac.doFinal(data)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Error calculating HMAC", e)
            throw EncryptionException("HMAC failed: ${e.message}")
        }
    }

    fun encryptAead(key: ByteArray, plaintext: ByteArray): EncryptedPayload {
        val nonce = randomNonce()
        return encryptAead(key, plaintext, nonce)
    }

    fun encryptAead(key: ByteArray, plaintext: ByteArray, nonce: ByteArray): EncryptedPayload {
        try {
            val cipher = Cipher.getInstance(AES_GCM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.copyOf(32), "AES"), spec)
            val encrypted = cipher.doFinal(plaintext)
            return EncryptedPayload(
                ciphertext = Base64.getEncoder().encodeToString(encrypted),
                nonce = Base64.getEncoder().encodeToString(nonce)
            )
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Error encrypting AEAD payload", e)
            throw EncryptionException("AEAD encryption failed: ${e.message}")
        }
    }

    fun decryptAead(key: ByteArray, payload: EncryptedPayload): ByteArray {
        try {
            val nonce = Base64.getDecoder().decode(payload.nonce)
            val ciphertext = Base64.getDecoder().decode(payload.ciphertext)
            val cipher = Cipher.getInstance(AES_GCM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.copyOf(32), "AES"), spec)
            return cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Error decrypting AEAD payload", e)
            throw EncryptionException("AEAD decryption failed: ${e.message}")
        }
    }

    fun randomNonce(size: Int = NONCE_BYTES): ByteArray = randomBytes(size)

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    fun hashPublicKey(publicKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(publicKey.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hashBytes)
    }

    fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decodeBase64(value: String): ByteArray = Base64.getDecoder().decode(value)
}

data class EncryptedPayload(
    val ciphertext: String,
    val nonce: String
)

class EncryptionException(message: String) : Exception(message)
