package bg.hristomanov.education.idempotency.service;

import bg.hristomanov.education.idempotency.api.CreatePaymentRequest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stable fingerprint за business payload-а.
 *
 * <p>Същият idempotency key не трябва да бъде използван за различна заявка.
 * Fingerprint-ът ни позволява да различим legitimate retry от accidental key reuse.</p>
 */
@Component
public class PaymentRequestFingerprint {

    public String calculate(CreatePaymentRequest request) {
        String canonical = normalize(request.customerId())
                + "|"
                + request.amount().stripTrailingZeros().toPlainString()
                + "|"
                + normalize(request.currency()).toUpperCase();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
