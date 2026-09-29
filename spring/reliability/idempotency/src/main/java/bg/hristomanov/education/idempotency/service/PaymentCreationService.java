package bg.hristomanov.education.idempotency.service;

import bg.hristomanov.education.idempotency.api.CreatePaymentRequest;
import bg.hristomanov.education.idempotency.domain.Payment;
import bg.hristomanov.education.idempotency.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Отделен transactional bean, за да може unique-constraint violation-ът
 * да rollback-не собствената creation transaction преди outer idempotency
 * flow-ът да прочете вече съществуващия payment.
 */
@Service
public class PaymentCreationService {

    private final PaymentRepository paymentRepository;

    public PaymentCreationService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment createNew(
            String idempotencyKey,
            String requestFingerprint,
            CreatePaymentRequest request
    ) {
        Payment payment = new Payment(
                idempotencyKey,
                requestFingerprint,
                request.customerId().trim(),
                request.amount(),
                request.currency().trim().toUpperCase()
        );

        /*
         * saveAndFlush() е умишлено: искаме unique constraint-ът върху
         * idempotency_key да бъде проверен вътре в тази transaction, за да
         * можем outer service-ът да обработи concurrent duplicate-а.
         */
        return paymentRepository.saveAndFlush(payment);
    }
}
