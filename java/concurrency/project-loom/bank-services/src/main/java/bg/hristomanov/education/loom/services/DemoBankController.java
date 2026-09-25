package bg.hristomanov.education.loom.services;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dummy downstream system, използван само за да направи concurrency поведението наблюдаемо.
 *
 * <p>В реално приложение account/loan/credit-score данните вероятно ще идват от отделни
 * microservices или database queries. Тук ги държим в един controller, но нарочно добавяме
 * различна latency, за да можем локално да видим разликата между sequential и concurrent flow.</p>
 *
 * <p>Този модул НЕ демонстрира production API design; той е controllable test fixture.</p>
 */
@RestController
@RequestMapping("/demo/customers")
public class DemoBankController {

    private static final int MAX_LIMITED_CONCURRENCY = 2;

    private final AtomicInteger limitedConcurrentRequests =
            new AtomicInteger();

    @GetMapping("/{id}")
    Customer customer(@PathVariable UUID id) {
        // Customer е prerequisite и е нарочно бърз, за да държим фокуса върху fan-out частта.
        return new Customer(id, "Demo Customer");
    }

    @GetMapping("/{id}/accounts")
    List<Account> accounts(@PathVariable UUID id) {
        // ~700 ms I/O wait симулира бавен downstream call.
        simulateLatency(700);
        return List.of(
                new Account("BG00DEMO000000000001", new BigDecimal("12500.00")),
                new Account("BG00DEMO000000000002", new BigDecimal("3200.00")));
    }

    @GetMapping("/{id}/loans")
    List<Loan> loans(@PathVariable UUID id) {
        // Loans е най-бавната required outer operation (~900 ms) в normal demo path-а.
        simulateLatency(900);
        return List.of(new Loan("LOAN-001", new BigDecimal("8400.00")));
    }

    @GetMapping("/{id}/credit-scores/{provider}")
    CreditScore creditScore(@PathVariable UUID id, @PathVariable String provider) {
        /*
         * Двата provider-а са нарочно с различна latency, за да демонстрираме
         * Joiner.anySuccessfulResultOrThrow():
         *
         * provider-a -> ~1200 ms
         * provider-b -> ~450 ms
         *
         * При нормално изпълнение provider-b печели. След успешния резултат sibling work
         * вече не е нужна и structured scope може да я cancel-не.
         */
        if ("provider-a".equals(provider)) {
            simulateLatency(1200);
            return new CreditScore(provider, 710);
        }

        simulateLatency(450);
        return new CreditScore(provider, 735);
    }

    /**
     * Downstream fixture за bounded-concurrency lab-а.
     *
     * <p>Endpoint-ът допуска максимум две едновременни заявки. Така можем да видим,
     * че "имаме много евтини virtual threads" не означава "downstream-ът има безкраен
     * capacity". Третата и следващите едновременни заявки получават HTTP 429.</p>
     */
    @GetMapping("/limited/pages/{page}")
    ResponseEntity<String> limitedPage(@PathVariable int page) {
        int activeRequests = limitedConcurrentRequests.incrementAndGet();

        try {
            if (activeRequests > MAX_LIMITED_CONCURRENCY) {
                return ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .body("Too many concurrent downstream requests");
            }

            simulateLatency(200);
            return ResponseEntity.ok("page-" + page);

        } finally {
            limitedConcurrentRequests.decrementAndGet();
        }
    }

    private void simulateLatency(long millis) {
        try {
            /*
             * Thread.sleep е умишлен избор за demo-то:
             * - представлява blocking wait;
             * - при virtual thread не трябва да мислим за него като за „загубен OS thread“;
             * - sleep е interruptible, затова можем да наблюдаваме cancellation semantics.
             *
             * В production никога не бихме добавяли sleep, за да симулираме реална latency.
             */
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            /*
             * Ако structured parent вече не се нуждае от тази работа, child thread може да
             * бъде interrupt-нат. Thread.sleep(...) реагира като хвърля InterruptedException.
             *
             * Важното е, че при хвърлянето на InterruptedException interrupted status-ът на
             * текущия thread се изчиства. Ако само wrap-нем exception-а, кодът по-нагоре вече
             * няма да може да види чрез isInterrupted(), че тази работа е била cancel-ната.
             *
             * Затова interrupt() тук възстановява flag-а. Не „прекъсваме thread-а втори път“;
             * запазваме cooperative cancellation signal-а, преди да propagate-нем failure-а.
             */
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Demo request was interrupted", e);
        }
    }

    // Records държат dummy transport model-а компактен; domain modelling не е целта на тази лаборатория.
    record Customer(UUID id, String name) {
    }

    record Account(String iban, BigDecimal balance) {
    }

    record Loan(String id, BigDecimal remainingAmount) {
    }

    record CreditScore(String provider, int score) {
    }
}
