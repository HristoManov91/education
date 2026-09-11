package bg.hristomanov.education.loom.services;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/demo/customers")
public class DemoBankController {

    @GetMapping("/{id}")
    Customer customer(@PathVariable UUID id) {
        return new Customer(id, "Demo Customer");
    }

    @GetMapping("/{id}/accounts")
    List<Account> accounts(@PathVariable UUID id) {
        simulateLatency(700);
        return List.of(
                new Account("BG00DEMO000000000001", new BigDecimal("12500.00")),
                new Account("BG00DEMO000000000002", new BigDecimal("3200.00")));
    }

    @GetMapping("/{id}/loans")
    List<Loan> loans(@PathVariable UUID id) {
        simulateLatency(900);
        return List.of(new Loan("LOAN-001", new BigDecimal("8400.00")));
    }

    @GetMapping("/{id}/credit-scores/{provider}")
    CreditScore creditScore(@PathVariable UUID id, @PathVariable String provider) {
        // provider-b е нарочно по-бърз, за да се види поведението на anySuccessfulOrThrow().
        if ("provider-a".equals(provider)) {
            simulateLatency(1200);
            return new CreditScore(provider, 710);
        }

        simulateLatency(450);
        return new CreditScore(provider, 735);
    }

    private void simulateLatency(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Demo request was interrupted", e);
        }
    }

    record Customer(UUID id, String name) {
    }

    record Account(String iban, BigDecimal balance) {
    }

    record Loan(String id, BigDecimal remainingAmount) {
    }

    record CreditScore(String provider, int score) {
    }
}
