package bg.hristomanov.education.idempotency.service;

import bg.hristomanov.education.idempotency.api.CreatePaymentRequest;
import bg.hristomanov.education.idempotency.api.PaymentResponse;
import bg.hristomanov.education.idempotency.domain.Payment;
import bg.hristomanov.education.idempotency.repository.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Idempotency orchestration за POST-like operation.
 *
 * <p>Първият read е optimization за нормалния retry path, но НЕ е correctness
 * гаранция. Два concurrent callers могат едновременно да видят "key липсва".
 * Последната correctness бариера е UNIQUE constraint в database-а.</p>
 */
@Service
public class IdempotentPaymentService {

    private static final int MAX_KEY_LENGTH = 255;

    private final PaymentRepository paymentRepository;
    private final PaymentCreationService paymentCreationService;
    private final PaymentRequestFingerprint fingerprint;

    public IdempotentPaymentService(
            PaymentRepository paymentRepository,
            PaymentCreationService paymentCreationService,
            PaymentRequestFingerprint fingerprint
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentCreationService = paymentCreationService;
        this.fingerprint = fingerprint;
    }

    public PaymentResponse create(
            String idempotencyKey,
            CreatePaymentRequest request
    ) {
        validate(idempotencyKey, request);

        String requestFingerprint = fingerprint.calculate(request);

        Optional<Payment> existing =
                paymentRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            return replayOrReject(
                    idempotencyKey,
                    requestFingerprint,
                    existing.get()
            );
        }

        try {
            Payment created = paymentCreationService.createNew(
                    idempotencyKey,
                    requestFingerprint,
                    request
            );
            return PaymentResponse.from(created, false);
        } catch (DataIntegrityViolationException exception) {
            /*
             * Check-then-act race:
             *
             * T1: find(key) -> empty
             * T2: find(key) -> empty
             * T1: INSERT -> wins
             * T2: INSERT -> UNIQUE violation
             *
             * След rollback на T2 creation transaction зареждаме winner-а
             * и го третираме като нормален replay.
             */
            Optional<Payment> winner =
                    paymentRepository.findByIdempotencyKey(idempotencyKey);

            if (winner.isEmpty()) {
                /*
                 * Constraint violation по друга причина. Не го маскираме
                 * като idempotency duplicate.
                 */
                throw exception;
            }

            return replayOrReject(
                    idempotencyKey,
                    requestFingerprint,
                    winner.get()
            );
        }
    }

    private PaymentResponse replayOrReject(
            String idempotencyKey,
            String requestFingerprint,
            Payment existing
    ) {
        if (!existing.getRequestFingerprint().equals(requestFingerprint)) {
            throw new IdempotencyKeyConflictException(idempotencyKey);
        }

        return PaymentResponse.from(existing, true);
    }

    private void validate(
            String idempotencyKey,
            CreatePaymentRequest request
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key is too long");
        }
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.customerId() == null || request.customerId().isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }
        if (request.amount() == null
                || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (request.currency() == null
                || request.currency().trim().length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter code");
        }
    }
}
