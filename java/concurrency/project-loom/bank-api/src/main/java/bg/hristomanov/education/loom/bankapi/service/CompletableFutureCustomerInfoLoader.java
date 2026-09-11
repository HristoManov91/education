package bg.hristomanov.education.loom.bankapi.service;

import bg.hristomanov.education.loom.bankapi.client.BankClients.AccountClient;
import bg.hristomanov.education.loom.bankapi.client.BankClients.CreditScoreClient;
import bg.hristomanov.education.loom.bankapi.client.BankClients.LoanClient;
import bg.hristomanov.education.loom.bankapi.context.RequestContext;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.Customer;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.CustomerInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Сравнителен пример. Работи, но lifecycle-ът и context propagation-ът трябва да се управляват
 * по-експлицитно, отколкото при StructuredTaskScope.
 */
public final class CompletableFutureCustomerInfoLoader {

    private final AccountClient accountClient;
    private final LoanClient loanClient;
    private final CreditScoreClient creditScoreClient;

    public CompletableFutureCustomerInfoLoader(
            AccountClient accountClient,
            LoanClient loanClient,
            CreditScoreClient creditScoreClient) {
        this.accountClient = accountClient;
        this.loanClient = loanClient;
        this.creditScoreClient = creditScoreClient;
    }

    public CustomerInfo load(Customer customer) {
        var metadata = RequestContext.current();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // ScopedValue се наследява автоматично от StructuredTaskScope children,
            // но не и от произволни threads. Тук затова re-bind-ваме context-а ръчно.
            var accounts = CompletableFuture.supplyAsync(
                    () -> RequestContext.call(metadata, () -> accountClient.getAccounts(customer.id())), executor);
            var loans = CompletableFuture.supplyAsync(
                    () -> RequestContext.call(metadata, () -> loanClient.getLoans(customer.id())), executor);
            var score = CompletableFuture.supplyAsync(
                    () -> RequestContext.call(metadata, () -> creditScoreClient.getFirstSuccessfulScore(customer.id())), executor);

            CompletableFuture.allOf(accounts, loans, score).join();
            return new CustomerInfo(accounts.join(), loans.join(), score.join());
        }
    }
}
