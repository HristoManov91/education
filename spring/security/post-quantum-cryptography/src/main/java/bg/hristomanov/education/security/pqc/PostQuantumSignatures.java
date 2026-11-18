package bg.hristomanov.education.security.pqc;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.util.Objects;

/** Учебен ML-DSA подпис. Не дефинира certificate/JOSE/PKI wire protocol. */
public final class PostQuantumSignatures {
    private static final SecureRandom RANDOM = new SecureRandom();

    private PostQuantumSignatures() {
    }

    public static KeyPair generateSigningKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ML-DSA");
        generator.initialize(NamedParameterSpec.ML_DSA_65, RANDOM);
        return generator.generateKeyPair();
    }

    public static byte[] sign(PrivateKey privateKey, byte[] message) throws GeneralSecurityException {
        Objects.requireNonNull(privateKey, "privateKey");
        Objects.requireNonNull(message, "message");
        Signature signature = Signature.getInstance("ML-DSA");
        signature.initSign(privateKey, RANDOM);
        signature.update(message);
        return signature.sign();
    }

    public static boolean verify(PublicKey publicKey, byte[] message, byte[] signedBytes)
            throws GeneralSecurityException {
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(signedBytes, "signedBytes");
        Signature signature = Signature.getInstance("ML-DSA");
        signature.initVerify(publicKey);
        signature.update(message);
        return signature.verify(signedBytes);
    }
}
