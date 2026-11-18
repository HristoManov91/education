package bg.hristomanov.education.security.pqc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;

class PostQuantumSignaturesTest {
    @Test
    void correctKeyAndMessageVerify() throws Exception {
        KeyPair signer = PostQuantumSignatures.generateSigningKeyPair();
        byte[] document = "audit-record:v1:record=42".getBytes(StandardCharsets.UTF_8);
        byte[] signedBytes = PostQuantumSignatures.sign(signer.getPrivate(), document);

        assertTrue(PostQuantumSignatures.verify(signer.getPublic(), document, signedBytes));
    }

    @Test
    void modifiedMessageFailsVerification() throws Exception {
        KeyPair signer = PostQuantumSignatures.generateSigningKeyPair();
        byte[] document = "audit-record:v1:record=42".getBytes(StandardCharsets.UTF_8);
        byte[] signedBytes = PostQuantumSignatures.sign(signer.getPrivate(), document);

        assertFalse(PostQuantumSignatures.verify(signer.getPublic(),
                "audit-record:v1:record=43".getBytes(StandardCharsets.UTF_8), signedBytes));
    }

    @Test
    void wrongSignerKeyFailsVerification() throws Exception {
        KeyPair signer = PostQuantumSignatures.generateSigningKeyPair();
        KeyPair anotherSigner = PostQuantumSignatures.generateSigningKeyPair();
        byte[] document = "audit-record:v1:record=42".getBytes(StandardCharsets.UTF_8);
        byte[] signedBytes = PostQuantumSignatures.sign(signer.getPrivate(), document);

        assertFalse(PostQuantumSignatures.verify(anotherSigner.getPublic(), document, signedBytes));
    }
}
