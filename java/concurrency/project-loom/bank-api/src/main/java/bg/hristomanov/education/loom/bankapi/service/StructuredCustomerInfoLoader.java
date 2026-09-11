package bg.hristomanov.education.loom.bankapi.service;

import bg.hristomanov.education.loom.bankapi.client.BankClients.AccountClient;
import bg.hristomanov.education.loom.bankapi.client.BankClients.CreditScoreClient;
import bg.hristomanov.education.loom.bankapi.client.BankClients.LoanClient;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.Customer;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.CustomerInfo;
import org.springframework.stereotype.Service;

import java.util.concurrent.StructuredTaskScope;

/**
 * Демонстрира основния Structured Concurrency use case в лабораторията.
 *
 * <p>След като вече имаме {@link Customer}, трите downstream операции са независими:
 * accounts, loans и credit score. Нито една от тях не се нуждае от резултата на другата,
 * затова е безопасно да ги стартираме concurrently.</p>
 *
 * <p>Важно е да не приемаме, че всяка последователност от три HTTP calls трябва автоматично
 * да стане concurrent. Fork-ваме само работа, за която dependency graph-ът го позволява.</p>
 *
 * <p>README: разделите „Structured Concurrency“ и „StructuredTaskScope стъпка по стъпка“.</p>
 */
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
        /*
         * open() създава lexical scope, който е owner на child задачите по-долу.
         *
         * Това е една от най-важните идеи на Structured Concurrency:
         * lifetime-ът на concurrent работата е видим от самата структура на кода.
         * Scope-ът започва тук и завършва при края на try блока.
         *
         * В Java 25 default policy е fail-fast. Ако required subtask fail-не,
         * останалата работа може да бъде cancel-ната и join() завършва с failure.
         */
        try (var scope = StructuredTaskScope.open()) {

            /*
             * fork() НЕ означава просто „пусни Runnable някъде“.
             * Казваме, че тази task е child на текущата structured операция.
             *
             * Тези три calls са предимно blocking HTTP I/O. Докато един virtual thread
             * чака downstream отговор, carrier thread може да изпълнява друга работа.
             *
             * Всяка променлива е Subtask<T> handle. Резултатът ще се прочете след join().
             */
            var accountsTask = scope.fork(() -> accountClient.getAccounts(customer.id()));
            var loansTask = scope.fork(() -> loanClient.getLoans(customer.id()));
            var creditScoreTask = scope.fork(() -> creditScoreClient.getFirstSuccessfulScore(customer.id()));

            /*
             * join() е structural join point-ът.
             *
             * До този ред трите child операции могат да вървят едновременно.
             * От този ред надолу business logic-ът изисква техните резултати.
             *
             * Това е по-лесно за reasoning от разпръснати Future#get()/join() calls,
             * защото имаме ясно място, където паралелните пътища отново се събират.
             */
            scope.join();

            /*
             * След успешен join() required subtasks са приключили успешно според policy-то
             * на този scope. Събираме резултатите обратно в един нормален domain object.
             *
             * Оттук нататък кодът отново е обикновен sequential business code.
             */
            return new CustomerInfo(
                    accountsTask.get(),
                    loansTask.get(),
                    creditScoreTask.get());

        } catch (InterruptedException e) {
            /*
             * InterruptedException е cooperative cancellation signal.
             *
             * Не трябва да го „изяждаме“. Когато го превръщаме в unchecked/application
             * exception, възстановяваме interrupt flag-а, за да може код по-нагоре по
             * call stack-а да разбере, че thread-ът е бил поискан за прекратяване.
             */
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Customer info loading was interrupted", e);
        }
    }
}
