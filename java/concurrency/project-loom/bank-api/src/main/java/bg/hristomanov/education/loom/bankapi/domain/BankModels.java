package bg.hristomanov.education.loom.bankapi.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class BankModels {

    private BankModels() {
    }

    public record Customer(UUID id, String name) {
    }

    public record Account(String iban, BigDecimal balance) {
    }

    public record Loan(String id, BigDecimal remainingAmount) {
    }

    public record CreditScore(String provider, int score) {
    }

    public record LoanApplicationRequest(UUID customerId, BigDecimal amount, String purpose) {
    }

    public record CustomerInfo(List<Account> accounts, List<Loan> loans, CreditScore creditScore) {
    }

    public record Offer(
            UUID customerId,
            boolean approved,
            BigDecimal amount,
            BigDecimal annualInterestRate,
            String reason) {
    }

    public record ThreadInfo(String thread, boolean virtual) {
    }
}
