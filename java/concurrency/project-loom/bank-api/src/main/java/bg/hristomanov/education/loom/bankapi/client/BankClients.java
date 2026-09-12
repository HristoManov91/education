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
 * Downstream HTTP clients (клиенти към услуги, които текущото приложение извиква)
 * за demo bank-services приложението.
 *
 * <p>Класовете са събрани в един outer class (външен контейнерен клас) само за да държим
 * учебния проект компактен. В реална система най-вероятно бихме ги разделили
 * в отделни файлове/packages.</p>
 *
 * <p>Използваме synchronous Spring {@link RestClient} (блокиращ HTTP клиент) нарочно.
 * Тези blocking HTTP calls (извиквания, които чакат отговор) са добър пример за workload
 * (тип натоварване), при който virtual threads могат да дадат high concurrency
 * (много едновременно обслужвани операции), без да пренаписваме business flow-а
 * като reactive callback pipeline.</p>
 */
public final class BankClients {

    private BankClients() {
        // Namespace/utility holder (клас-контейнер): не създаваме instance на outer class-а.
    }

    @Component
    public static class CustomerClient {
        private final RestClient restClient;

        public CustomerClient(RestClient restClient) {
            this.restClient = restClient;
        }

        public Customer getCustomer(UUID customerId) {
            /*
             * Customer lookup е prerequisite (предварително нужна стъпка) за останалия fan-out
             * (разклоняването към няколко независими операции): първо искаме да знаем
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
             * 1) requestId — доказва ScopedValue context propagation-а
             *    (пренасянето на request контекста към child задачите);
             * 2) текущия Thread — показва, че sibling calls
             *    (паралелни извиквания на едно и също ниво) са в различни threads;
             * 3) isVirtual — доказва virtual-thread execution-а.
             *
             * Това е observability част (наблюдаемост на поведението) от лабораторията,
             * не просто debug noise (излишен диагностичен шум).
             */
            log.info("requestId={} operation=accounts thread={} virtual={}",
                    RequestContext.current().requestId(), Thread.currentThread(), Thread.currentThread().isVirtual());

            Account[] result = restClient.get()
                    .uri("/demo/customers/{id}/accounts", customerId)
                    .retrieve()
                    .body(Account[].class);

            // RestClient body() може да върне null. За demo domain-а предпочитаме empty collection (празна колекция).
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
            // Същата observability идея (наблюдаемост) като при AccountClient — виж коментара там.
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
         * Пита два независими credit-score provider-а (доставчика на кредитен рейтинг)
         * и връща първия УСПЕШЕН резултат.
         *
         * <p>Това е nested Structured Concurrency пример (structured scope вътре в друг scope):
         * самият CreditScoreClient е child task (дъщерна задача) на outer customer-info scope,
         * но вътре създава собствен scope с още две children.</p>
         *
         * <p>„Първият успешен“ е важно различно изискване от „първият приключил“. Ако единият
         * provider fail-не бързо, не искаме неговия exception, а продължаваме да чакаме другия.</p>
         */
        public CreditScore getFirstSuccessfulScore(UUID customerId) {
            /*
             * Избираме anySuccessfulResultOrThrow(), защото тук business requirement-ът е:
             * „първият УСПЕШЕН provider е достатъчен“.
             *
             * Това е различно от outer StructuredCustomerInfoLoader, където accounts + loans +
             * credit score са всички задължителни и default awaitAllSuccessfulOrThrow() е правилният избор.
             *
             * Java 25 Joiner policy (правилото кога имаме достатъчен резултат):
             * - fork-ваме няколко candidate subtasks (кандидат дъщерни задачи);
             * - scope.join() приключва, когато има успешен резултат;
             * - ненужната sibling работа (останалата паралелна работа на същото ниво)
             *   може да бъде cancel-ната;
             * - ако всички subtasks fail-нат, join() завършва с failure.
             *
             * StructuredTaskScope<CreditScore, CreditScore> показва двата важни type параметъра:
             * - първият CreditScore е допустимият result type на fork-натите subtasks;
             * - вторият CreditScore е резултатът, който join() връща за тази Joiner policy.
             *
             * Другите основни Joiner policy-та са описани в project-loom/JOINER-POLICIES.md.
             * Official Java 25 API:
             * https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.Joiner.html
             *
             * Java 26 преименува factory метода на anySuccessfulOrThrow().
             * Подробно: project-loom/JAVA-26.md.
             */
            try (StructuredTaskScope<CreditScore, CreditScore> scope = StructuredTaskScope.open(
                    StructuredTaskScope.Joiner.<CreditScore>anySuccessfulResultOrThrow())) {

                scope.fork(() -> getScore(customerId, "provider-a"));
                scope.fork(() -> getScore(customerId, "provider-b"));

                /*
                 * Тук join() директно връща CreditScore, защото Joiner определя result type-а
                 * (типа на крайния резултат). Това е различно от outer default scope,
                 * където след join() четем Subtask#get().
                 */
                return scope.join();

            } catch (InterruptedException e) {
                /*
                 * join() е interruptible. Когато хвърли InterruptedException, interrupted status-ът
                 * на owner thread-а е изчистен. Понеже тук не propagate-ваме checked exception-а,
                 * а го wrap-ваме в IllegalStateException, възстановяваме flag-а ръчно.
                 *
                 * Това interrupt() НЕ прекъсва thread-а „още веднъж“; то маркира текущия thread
                 * отново като interrupted, за да може caller/framework по-нагоре да види
                 * cancellation signal-а чрез Thread.currentThread().isInterrupted().
                 */
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Credit score lookup was interrupted", e);
            }
        }

        private CreditScore getScore(UUID customerId, String provider) {
            /*
             * RequestContext.current() работи и тук, въпреки че сме в nested child task
             * (дъщерна задача във вложен scope). Това демонстрира ScopedValue inheritance
             * (наследяването на контекста) през structured task tree-а.
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
