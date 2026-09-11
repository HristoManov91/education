package bg.hristomanov.education.loom.bankapi.service;

import bg.hristomanov.education.loom.bankapi.client.BankClients.AccountClient;
import bg.hristomanov.education.loom.bankapi.client.BankClients.CreditScoreClient;
import bg.hristomanov.education.loom.bankapi.client.BankClients.LoanClient;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.Customer;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.CustomerInfo;
import org.springframework.stereotype.Service;

import java.util.concurrent.StructuredTaskScope;

@Service
public class StructuredCustomerInfoLoader {

    private final AccountClient accountClient;
    private final LoanClient loanClient;
    private final CreditScoreClient creditScoreClient;

    public StructuredCustomerInfoLoader(
            AccountClient accountClient,
            LoanClient loanClient,
            CreditScoreClient creditScoreClient) {
        this.accountClient = accountClient;
        this.loanClient = loanClient;
        this.creditScoreClient = creditScoreClient;
    }

    public CustomerInfo load(Customer customer) {
        try (var scope = StructuredTaskScope.open()) {
            // Трите операции са независими и могат да чакат I/O паралелно.
            var accountsTask = scope.fork(() -> accountClient.getAccounts(customer.id()));
            var loansTask = scope.fork(() -> loanClient.getLoans(customer.id()));
            var creditScoreTask = scope.fork(() -> creditScoreClient.getFirstSuccessfulScore(customer.id()));

            // Това е ясната структурна граница: методът не може да приключи,
            // докато неговите child задачи не са приключили или отменени.
            scope.join();

            return new CustomerInfo(
                    accountsTask.get(),
                    loansTask.get(),
                    creditScoreTask.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Customer info loading was interrupted", e);
        }
    }
}
