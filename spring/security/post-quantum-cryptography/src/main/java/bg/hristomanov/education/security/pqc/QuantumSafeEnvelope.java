package bg.hristomanov.education.security.pqc;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.NamedParameterSpec;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.KEM;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;

/**
 * Учебен envelope: ML-KEM установява таен ключ, HKDF го отделя за конкретна
 * употреба, а AES-GCM осигурява confidentiality и authentication.
 *
 * Това НЕ е стандартизиран wire format или заместител на TLS/HPKE.
 */
public final class QuantumSafeEnvelope {
    private static final int SALT_BYTES = 32;
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_KEY_BYTES = 32;
    private static final byte[] KEY_CONTEXT =
            "education:pqc:envelope:v1:aes-256-gcm".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    private QuantumSafeEnvelope() {
    }

    public static KeyPair generateRecipientKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ML-KEM");
        generator.initialize(NamedParameterSpec.ML_KEM_768, RANDOM);
        return generator.generateKeyPair();
    }

    public static Envelope encrypt(PublicKey recipientKey, byte[] plaintext, byte[] associatedData)
            throws GeneralSecurityException {
        Objects.requireNonNull(recipientKey, "recipientKey");
        Objects.requireNonNull(plaintext, "plaintext");
        Objects.requireNonNull(associatedData, "associatedData");

        // Всеки envelope има нов ML-KEM secret, salt и GCM nonce.
        KEM kem = KEM.getInstance("ML-KEM");
        KEM.Encapsulated encapsulated = kem.newEncapsulator(recipientKey, RANDOM).encapsulate();
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] nonce = randomBytes(GCM_NONCE_BYTES);
        SecretKey aesKey = deriveAesKey(encapsulated.key(), salt);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        cipher.updateAAD(associatedData);
        byte[] encryptedPayload = cipher.doFinal(plaintext);
        return new Envelope(encapsulated.encapsulation(), salt, nonce, encryptedPayload);
    }

    public static byte[] decrypt(PrivateKey recipientKey, Envelope envelope, byte[] associatedData)
            throws GeneralSecurityException {
        Objects.requireNonNull(recipientKey, "recipientKey");
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(associatedData, "associatedData");

        KEM kem = KEM.getInstance("ML-KEM");
        KEM.Decapsulator decapsulator = kem.newDecapsulator(recipientKey);

        // При валиден по дължина, но повреден ML-KEM ciphertext може да има
        // implicit rejection (различен secret), НЕ непременно exception.
        SecretKey sharedSecret = decapsulator.decapsulate(envelope.kemCiphertext());
        SecretKey aesKey = deriveAesKey(sharedSecret, envelope.salt());

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey,
                new GCMParameterSpec(GCM_TAG_BITS, envelope.nonce()));
        cipher.updateAAD(associatedData);
        return cipher.doFinal(envelope.encryptedPayload());
    }

    private static SecretKey deriveAesKey(SecretKey sharedSecret, byte[] salt)
            throws GeneralSecurityException {
        HKDFParameterSpec.ExtractThenExpand parameters =
                HKDFParameterSpec.ofExtract()
                        .addIKM(sharedSecret)
                        .addSalt(salt)
                        .thenExpand(KEY_CONTEXT, AES_KEY_BYTES);
        return KDF.getInstance("HKDF-SHA256").deriveKey("AES", parameters);
    }

    private static byte[] randomBytes(int count) {
        byte[] output = new byte[count];
        RANDOM.nextBytes(output);
        return output;
    }

    /**
     * Byte-array полетата са защитени с копиране при създаване и четене.
     * Лабораторията не дефинира сериализация, идентификатор на ключ или ротация.
     */
    public record Envelope(byte[] kemCiphertext, byte[] salt, byte[] nonce, byte[] encryptedPayload) {
        public Envelope {
            Objects.requireNonNull(kemCiphertext, "kemCiphertext");
            Objects.requireNonNull(salt, "salt");
            Objects.requireNonNull(nonce, "nonce");
            Objects.requireNonNull(encryptedPayload, "encryptedPayload");
            if (kemCiphertext.length == 0 || salt.length != SALT_BYTES
                    || nonce.length != GCM_NONCE_BYTES || encryptedPayload.length < 16) {
                throw new IllegalArgumentException("Malformed educational envelope");
            }
            kemCiphertext = kemCiphertext.clone();
            salt = salt.clone();
            nonce = nonce.clone();
            encryptedPayload = encryptedPayload.clone();
        }

        @Override
        public byte[] kemCiphertext() {
            return kemCiphertext.clone();
        }

        @Override
        public byte[] salt() {
            return salt.clone();
        }

        @Override
        public byte[] nonce() {
            return nonce.clone();
        }

        @Override
        public byte[] encryptedPayload() {
            return encryptedPayload.clone();
        }
    }
}
