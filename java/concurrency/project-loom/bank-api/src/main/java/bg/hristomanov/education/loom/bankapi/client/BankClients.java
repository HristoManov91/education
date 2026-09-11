package bg.hristomanov.education.loom.bankapi.client;

import bg.hristomanov.education.loom.bankapi.context.RequestContext;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.Account;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.CreditScore;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.Customer;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.Loan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.StructuredTaskScope;

public final class BankClients {

    private BankClients() {
    }

    @Component
    public static class CustomerClient {
        private final RestClient restClient;

        public CustomerClient(RestClient restClient) {
            this.restClient = restClient;
        }

        public Customer getCustomer(UUID customerId) {
            return restClient.get()
                    .uri("/demo/customers/{id}", customerId)
                    .retrieve()
                    .body(Customer.class);
        }
    }

    @Component
    public static class AccountClient {
        private static final Logger log = LoggerFactory.getLogger(AccountClient.class);
        private final RestClient restClient;

        public AccountClient(RestClient restClient) {
            this.restClient = restClient;
        }

        public List<Account> getAccounts(UUID customerId) {
            log.info("requestId={} operation=accounts thread={} virtual={}",
                    RequestContext.current().requestId(), Thread.currentThread(), Thread.currentThread().isVirtual());
            Account[] result = restClient.get()
                    .uri("/demo/customers/{id}/accounts", customerId)
                    .retrieve()
                    .body(Account[].class);
            return result == null ? List.of() : Arrays.asList(result);
        }
    }

    @Component
    public static class LoanClient {
        private static final Logger log = LoggerFactory.getLogger(LoanClient.class);
        private final RestClient restClient;

        public LoanClient(RestClient restClient) {
            this.restClient = restClient;
        }

        public List<Loan> getLoans(UUID customerId) {
            log.info("requestId={} operation=loans thread={} virtual={}",
                    RequestContext.current().requestId(), Thread.currentThread(), Thread.currentThread().isVirtual());
            Loan[] result = restClient.get()
                    .uri("/demo/customers/{id}/loans", customerId)
                    .retrieve()
                    .body(Loan[].class);
            return result == null ? List.of() : Arrays.asList(result);
        }
    }

    @Component
    public static class CreditScoreClient {
        private static final Logger log = LoggerFactory.getLogger(CreditScoreClient.class);
        private final RestClient restClient;

        public CreditScoreClient(RestClient restClient) {
            this.restClient = restClient;
        }

        public CreditScore getFirstSuccessfulScore(UUID customerId) {
            // Java 25: този Joiner връща резултата от първата успешно приключила subtask.
            // Java 26: методът е преименуван на anySuccessfulOrThrow(); виж project-loom/JAVA-26.md.
            try (var scope = StructuredTaskScope.open(
                    StructuredTaskScope.Joiner.<CreditScore>anySuccessfulResultOrThrow())) {
                scope.fork(() -> getScore(customerId, "provider-a"));
                scope.fork(() -> getScore(customerId, "provider-b"));
                return scope.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Credit score lookup was interrupted", e);
            }
        }

        private CreditScore getScore(UUID customerId, String provider) {
            log.info("requestId={} operation=credit-score provider={} thread={} virtual={}",
                    RequestContext.current().requestId(), provider,
                    Thread.currentThread(), Thread.currentThread().isVirtual());
            return restClient.get()
                    .uri("/demo/customers/{id}/credit-scores/{provider}", customerId, provider)
                    .retrieve()
                    .body(CreditScore.class);
        }
    }
}
