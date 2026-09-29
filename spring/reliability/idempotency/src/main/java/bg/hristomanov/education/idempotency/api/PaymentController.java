package bg.hristomanov.education.idempotency.api;

import bg.hristomanov.education.idempotency.service.IdempotentPaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final IdempotentPaymentService paymentService;

    public PaymentController(IdempotentPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreatePaymentRequest request
    ) {
        PaymentResponse response =
                paymentService.create(idempotencyKey, request);

        HttpStatus status = response.replayed()
                ? HttpStatus.OK
                : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(response);
    }
}
