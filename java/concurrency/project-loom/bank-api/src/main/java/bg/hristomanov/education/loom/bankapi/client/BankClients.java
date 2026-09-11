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

/**
 * Downstream HTTP clients за demo bank-services приложението.
 *
 * <p>Класовете са събрани в един outer class само за да държим учебния проект компактен.
 * В реална система най-вероятно бихме ги разделили в отделни файлове/packages.</p>
 *
 * <p>Използваме synchronous Spring {@link RestClient} нарочно. Тези blocking HTTP calls
 * са добър пример за workload, при който virtual threads могат да дадат high concurrency,
 * без да пренаписваме business flow-а като reactive callback pipeline.</p>
 */
public final class BankClients {

    private BankClients() {
        // Namespace/utility holder: не създаваме instance на outer class-а.
    }

    @Component
    public static class CustomerClient {
        private final RestClient restClient;

        public CustomerClient(RestClient restClient) {
            this.restClient = restClient;
        }

        public Customer getCustomer(UUID customerId) {
            /*
             * Customer lookup е prerequisite за останалия fan-out: първо искаме да знаем
             * за кой customer работим. Затова този call в LoanApplicationService остава
             * преди StructuredTaskScope, вместо механично да fork-ваме абсолютно всичко.
             */
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
            /*
             * Логваме три неща нарочно:
             * 1) requestId — доказва ScopedValue context propagation-а;
             * 2) текущия Thread — показва, че sibling calls са в различни threads;
             * 3) isVirtual — доказва virtual-thread execution-а.
             *
             * Това е observability част от лабораторията, не просто debug noise.
             */
            log.info("requestId={} operation=accounts thread={} virtual={}",
                    RequestContext.current().requestId(), Thread.currentThread(), Thread.currentThread().isVirtual());

            Account[] result = restClient.get()
                    .uri("/demo/customers/{id}/accounts", customerId)
                    .retrieve()
                    .body(Account[].class);

            // RestClient body() може да върне null. За demo domain-а предпочитаме empty collection.
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
            // Същата observability идея като при AccountClient — виж коментара там.
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

        /**
         * Пита два независими credit-score provider-а и връща първия УСПЕШЕН резултат.
         *
         * <p>Това е nested Structured Concurrency пример: самият CreditScoreClient е child
         * task на outer customer-info scope, но вътре създава собствен scope с още две children.</p>
         *
         * <p>„Първият успешен“ е важно различно изискване от „първият приключил“. Ако единият
         * provider fail-не бързо, не искаме неговия exception, а продължаваме да чакаме другия.</p>
         */
        public CreditScore getFirstSuccessfulScore(UUID customerId) {
            /*
             * Java 25 Joiner policy:
             * - fork-ваме няколко candidate subtasks;
             * - scope.join() приключва, когато има успешен резултат;
             * - ненужната sibling работа може да бъде cancel-ната;
             * - ако всички subtasks fail-нат, join() завършва с failure.
             *
             * Java 26 преименува factory метода на anySuccessfulOrThrow().
             * Подробно: project-loom/JAVA-26.md.
             */
            try (var scope = StructuredTaskScope.open(
                    StructuredTaskScope.Joiner.<CreditScore>anySuccessfulResultOrThrow())) {

                scope.fork(() -> getScore(customerId, "provider-a"));
                scope.fork(() -> getScore(customerId, "provider-b"));

                /*
                 * Тук join() директно връща CreditScore, защото Joiner определя result type-а.
                 * Това е различно от outer default scope, където след join() четем Subtask#get().
                 */
                return scope.join();

            } catch (InterruptedException e) {
                // Не губим cooperative cancellation signal-а при wrapping на exception-а.
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Credit score lookup was interrupted", e);
            }
        }

        private CreditScore getScore(UUID customerId, String provider) {
            /*
             * RequestContext.current() работи и тук, въпреки че сме в nested child task.
             * Това демонстрира ScopedValue inheritance през structured task tree-а.
             */
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
