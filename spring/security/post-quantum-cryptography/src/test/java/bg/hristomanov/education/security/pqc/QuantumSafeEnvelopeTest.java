package bg.hristomanov.education.security.pqc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class QuantumSafeEnvelopeTest {
    private static final byte[] AAD = "tenant=42;record=abc".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PLAIN = "long-lived confidential record".getBytes(StandardCharsets.UTF_8);

    @Test
    void recipientCanDecryptWithMatchingContext() throws Exception {
        KeyPair recipient = QuantumSafeEnvelope.generateRecipientKeyPair();

        QuantumSafeEnvelope.Envelope envelope =
                QuantumSafeEnvelope.encrypt(recipient.getPublic(), PLAIN, AAD);

        assertArrayEquals(PLAIN,
                QuantumSafeEnvelope.decrypt(recipient.getPrivate(), envelope, AAD));
        assertFalse(Arrays.equals(PLAIN, envelope.encryptedPayload()));
    }

    @Test
    void wrongRecipientCannotDecrypt() throws Exception {
        KeyPair recipient = QuantumSafeEnvelope.generateRecipientKeyPair();
        KeyPair attacker = QuantumSafeEnvelope.generateRecipientKeyPair();
        QuantumSafeEnvelope.Envelope envelope =
                QuantumSafeEnvelope.encrypt(recipient.getPublic(), PLAIN, AAD);

        assertThrows(GeneralSecurityException.class,
                () -> QuantumSafeEnvelope.decrypt(attacker.getPrivate(), envelope, AAD));
    }

    @Test
    void changedBusinessContextFailsAeadAuthentication() throws Exception {
        KeyPair recipient = QuantumSafeEnvelope.generateRecipientKeyPair();
        QuantumSafeEnvelope.Envelope envelope =
                QuantumSafeEnvelope.encrypt(recipient.getPublic(), PLAIN, AAD);

        assertThrows(GeneralSecurityException.class, () ->
                QuantumSafeEnvelope.decrypt(recipient.getPrivate(), envelope,
                        "tenant=43;record=abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void modifiedPayloadOrKemCiphertextFailsAuthentication() throws Exception {
        KeyPair recipient = QuantumSafeEnvelope.generateRecipientKeyPair();
        QuantumSafeEnvelope.Envelope envelope =
                QuantumSafeEnvelope.encrypt(recipient.getPublic(), PLAIN, AAD);

        byte[] alteredPayload = envelope.encryptedPayload();
        alteredPayload[alteredPayload.length - 1] ^= 1;
        QuantumSafeEnvelope.Envelope payloadTampered = new QuantumSafeEnvelope.Envelope(
                envelope.kemCiphertext(), envelope.salt(), envelope.nonce(), alteredPayload);
        assertThrows(GeneralSecurityException.class,
                () -> QuantumSafeEnvelope.decrypt(recipient.getPrivate(), payloadTampered, AAD));

        byte[] alteredEncapsulation = envelope.kemCiphertext();
        alteredEncapsulation[0] ^= 1;
        QuantumSafeEnvelope.Envelope kemTampered = new QuantumSafeEnvelope.Envelope(
                alteredEncapsulation, envelope.salt(), envelope.nonce(), envelope.encryptedPayload());
        // ML-KEM може да върне друг secret; AES-GCM трябва да отхвърли payload-а.
        assertThrows(GeneralSecurityException.class,
                () -> QuantumSafeEnvelope.decrypt(recipient.getPrivate(), kemTampered, AAD));
    }

    @Test
    void truncatedEncapsulationIsRejected() throws Exception {
        KeyPair recipient = QuantumSafeEnvelope.generateRecipientKeyPair();
        QuantumSafeEnvelope.Envelope envelope =
                QuantumSafeEnvelope.encrypt(recipient.getPublic(), PLAIN, AAD);
        byte[] truncated = Arrays.copyOf(envelope.kemCiphertext(), envelope.kemCiphertext().length - 1);
        QuantumSafeEnvelope.Envelope malformed = new QuantumSafeEnvelope.Envelope(
                truncated, envelope.salt(), envelope.nonce(), envelope.encryptedPayload());

        assertThrows(GeneralSecurityException.class,
                () -> QuantumSafeEnvelope.decrypt(recipient.getPrivate(), malformed, AAD));
    }

    @Test
    void envelopeDefendsAgainstExternalArrayMutation() throws Exception {
        KeyPair recipient = QuantumSafeEnvelope.generateRecipientKeyPair();
        QuantumSafeEnvelope.Envelope envelope =
                QuantumSafeEnvelope.encrypt(recipient.getPublic(), PLAIN, AAD);
        byte[] snapshot = envelope.encryptedPayload();
        byte[] accessorValue = envelope.encryptedPayload();
        accessorValue[0] ^= 1;

        assertArrayEquals(snapshot, envelope.encryptedPayload());
        assertArrayEquals(PLAIN,
                QuantumSafeEnvelope.decrypt(recipient.getPrivate(), envelope, AAD));
    }
}
