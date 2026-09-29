package bg.hristomanov.education.idempotency;

import bg.hristomanov.education.idempotency.api.CreatePaymentRequest;
import bg.hristomanov.education.idempotency.api.PaymentResponse;
import bg.hristomanov.education.idempotency.repository.PaymentRepository;
import bg.hristomanov.education.idempotency.service.IdempotencyKeyConflictException;
import bg.hristomanov.education.idempotency.service.IdempotentPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IdempotentPaymentServiceTest {

    private final IdempotentPaymentService paymentService;
    private final PaymentRepository paymentRepository;

    @Autowired
    IdempotentPaymentServiceTest(
            IdempotentPaymentService paymentService,
            PaymentRepository paymentRepository
    ) {
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
    }

    @BeforeEach
    void cleanDatabase() {
        paymentRepository.deleteAll();
    }

    @Test
    void exactRetryReturnsTheSamePaymentInsteadOfCreatingAnotherOne() {
        CreatePaymentRequest firstRequest = request("120.00", "EUR");
        CreatePaymentRequest semanticallySameRetry = request("120.0", "eur");

        PaymentResponse first =
                paymentService.create("payment-key-001", firstRequest);
        PaymentResponse replay =
                paymentService.create("payment-key-001", semanticallySameRetry);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.paymentId()).isEqualTo(first.paymentId());
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() {
        paymentService.create(
                "payment-key-conflict",
                request("120.00", "EUR")
        );

        assertThatThrownBy(() -> paymentService.create(
                "payment-key-conflict",
                request("999.00", "EUR")
        ))
                .isInstanceOf(IdempotencyKeyConflictException.class)
                .hasMessageContaining("payment-key-conflict");

        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void failedValidationDoesNotConsumeTheIdempotencyKey() {
        assertThatThrownBy(() -> paymentService.create(
                "payment-key-validation",
                request("-1.00", "EUR")
        ))
                .isInstanceOf(IllegalArgumentException.class);

        PaymentResponse validRetry = paymentService.create(
                "payment-key-validation",
                request("50.00", "EUR")
        );

        assertThat(validRetry.replayed()).isFalse();
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateRequestsCreateExactlyOnePayment() throws Exception {
        int callers = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PaymentResponse>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(callers)) {
            for (int index = 0; index < callers; index++) {
                futures.add(
                        executor.submit(() -> {
                            start.await();
                            return paymentService.create(
                                    "payment-key-concurrent",
                                    request("75.00", "EUR")
                            );
                        })
                );
            }

            start.countDown();

            List<PaymentResponse> responses = new ArrayList<>();
            for (Future<PaymentResponse> future : futures) {
                responses.add(future.get(10, TimeUnit.SECONDS));
            }

            Set<Long> paymentIds = responses.stream()
                    .map(PaymentResponse::paymentId)
                    .collect(Collectors.toSet());

            long originalResponses = responses.stream()
                    .filter(response -> !response.replayed())
                    .count();

            assertThat(paymentIds).hasSize(1);
            assertThat(originalResponses).isEqualTo(1);
            assertThat(paymentRepository.count()).isEqualTo(1);
        }
    }

    private CreatePaymentRequest request(String amount, String currency) {
        return new CreatePaymentRequest(
                "C-42",
                new BigDecimal(amount),
                currency
        );
    }
}
