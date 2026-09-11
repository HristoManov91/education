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
 * Сравнителен вариант на същия use case с CompletableFuture.
 *
 * <p>Този class не съществува, за да показва че CompletableFuture е „лош“. Идеята е да
 * сравним два различни programming model-а:</p>
 *
 * <ul>
 *   <li>CompletableFuture мисли основно в future values и async composition;</li>
 *   <li>Structured Concurrency мисли в parent operation + child task lifecycle.</li>
 * </ul>
 *
 * <p>За request-oriented fan-out/fan-in flow вторият модел често прави ownership-а,
 * cancellation-а и context propagation-а по-видими.</p>
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
        /*
         * Вземаме metadata ПРЕДИ да стартираме async tasks.
         *
         * Причината: virtual threads, създадени от този ExecutorService, не са автоматично
         * structured children на текущата операция. ScopedValue inheritance-ът, който имаме
         * при StructuredTaskScope, не трябва да се предполага тук.
         */
        var metadata = RequestContext.current();

        /*
         * newVirtualThreadPerTaskExecutor() създава НОВ virtual thread за всяка submitted task.
         * Това е правилният virtual-thread модел: thread-per-task, а не fixed pool от virtual threads.
         *
         * try-with-resources затваря executor-а след края на метода и изчаква submitted tasks.
         */
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            /*
             * Re-bind-ваме същото immutable request metadata във всяка async task.
             * Без това AccountClient/LoanClient/CreditScoreClient не могат да прочетат
             * RequestContext.current() в тези independently-created virtual threads.
             */
            var accounts = CompletableFuture.supplyAsync(
                    () -> RequestContext.call(metadata, () -> accountClient.getAccounts(customer.id())), executor);

            var loans = CompletableFuture.supplyAsync(
                    () -> RequestContext.call(metadata, () -> loanClient.getLoans(customer.id())), executor);

            var score = CompletableFuture.supplyAsync(
                    () -> RequestContext.call(metadata, () -> creditScoreClient.getFirstSuccessfulScore(customer.id())), executor);

            /*
             * allOf() е fan-in point-ът в CompletableFuture варианта.
             * Той самият връща CompletableFuture<Void>, затова после четем всеки резултат
             * поотделно с join().
             *
             * Сравни това с StructuredTaskScope: там fork-натите tasks и join point-ът
             * са част от една explicit lexical task structure.
             */
            CompletableFuture.allOf(accounts, loans, score).join();

            return new CustomerInfo(
                    accounts.join(),
                    loans.join(),
                    score.join());
        }
    }
}
