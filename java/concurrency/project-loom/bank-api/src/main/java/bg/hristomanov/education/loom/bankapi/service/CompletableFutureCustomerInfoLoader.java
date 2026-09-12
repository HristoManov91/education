package bg.hristomanov.education.loom.bankapi.service;

import bg.hristomanov.education.loom.bankapi.client.BankClients.AccountClient;
import bg.hristomanov.education.loom.bankapi.client.BankClients.CreditScoreClient;
import bg.hristomanov.education.loom.bankapi.client.BankClients.LoanClient;
import bg.hristomanov.education.loom.bankapi.context.RequestContext;
import bg.hristomanov.education.loom.bankapi.context.RequestContext.RequestMetadata;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.Account;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.CreditScore;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.Customer;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.CustomerInfo;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.Loan;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Сравнителен вариант на същия use case (конкретен бизнес сценарий) с CompletableFuture.
 *
 * <p>Този class не съществува, за да показва че CompletableFuture е „лош“. Идеята е да
 * сравним два различни programming model-а (начина, по който структурираме concurrent кода):</p>
 *
 * <ul>
 *   <li>CompletableFuture мисли основно в future values (резултати, които ще бъдат налични по-късно)
 *       и async composition (комбиниране на асинхронни операции);</li>
 *   <li>Structured Concurrency мисли в parent operation + child task lifecycle
 *       (родителска операция + жизнен цикъл на дъщерните задачи).</li>
 * </ul>
 *
 * <p>За request-oriented fan-out/fan-in flow (заявка, която се разклонява към няколко операции
 * и после събира резултатите им) вторият модел често прави ownership-а
 * (кой управлява задачите), cancellation-а (как се прекратяват ненужните задачи)
 * и context propagation-а (как request контекстът се пренася към child задачите) по-видими.</p>
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
         * Вземаме metadata ПРЕДИ да стартираме async tasks (асинхронните задачи).
         *
         * Причината: virtual threads, създадени от този ExecutorService, не са автоматично
         * structured children (дъщерни задачи в същата structured операция) на текущата операция.
         * ScopedValue inheritance-ът (автоматичното наследяване на контекста), който имаме
         * при StructuredTaskScope, не трябва да се предполага тук.
         *
         * Explicit type-ът показва директно, че RequestContext.current() връща RequestMetadata.
         */
        RequestMetadata metadata = RequestContext.current();

        /*
         * newVirtualThreadPerTaskExecutor() създава НОВ virtual thread за всяка submitted task
         * (подадена за изпълнение задача). Това е правилният virtual-thread модел: thread-per-task
         * (нова нишка за всяка задача), а не fixed pool (фиксиран пул) от virtual threads.
         *
         * try-with-resources затваря executor-а след края на метода и изчаква submitted tasks.
         * Изписваме ExecutorService вместо var, за да се вижда публичният API type на factory метода.
         */
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            /*
             * Re-bind-ваме (свързваме отново) същото immutable request metadata
             * (неизменяеми данни за заявката) във всяка async task.
             * Без това AccountClient/LoanClient/CreditScoreClient не могат да прочетат
             * RequestContext.current() в тези independently-created virtual threads
             * (virtual threads, създадени независимо от текущата structured операция).
             *
             * Тук explicit generic типовете са особено полезни: още от декларацията се вижда
             * какъв резултат носи всеки CompletableFuture.
             */
            CompletableFuture<List<Account>> accounts = CompletableFuture.supplyAsync(
                    () -> RequestContext.call(metadata, () -> accountClient.getAccounts(customer.id())), executor);

            CompletableFuture<List<Loan>> loans = CompletableFuture.supplyAsync(
                    () -> RequestContext.call(metadata, () -> loanClient.getLoans(customer.id())), executor);

            CompletableFuture<CreditScore> score = CompletableFuture.supplyAsync(
                    () -> RequestContext.call(metadata, () -> creditScoreClient.getFirstSuccessfulScore(customer.id())), executor);

            /*
             * allOf() е fan-in point-ът (точката, в която паралелните операции отново се събират)
             * в CompletableFuture варианта. Той самият връща CompletableFuture<Void>, затова после
             * четем всеки резултат поотделно с join().
             *
             * Сравни това с StructuredTaskScope: там fork-натите tasks и join point-ът
             * са част от една explicit lexical task structure
             * (явно видима в кода структура с начало и край на child задачите).
             */
            CompletableFuture.allOf(accounts, loans, score).join();

            return new CustomerInfo(
                    accounts.join(),
                    loans.join(),
                    score.join());
        }
    }
}
