package bg.hristomanov.education.loom.bankapi.service;

import bg.hristomanov.education.loom.bankapi.client.BankClients.AccountClient;
import bg.hristomanov.education.loom.bankapi.client.BankClients.CreditScoreClient;
import bg.hristomanov.education.loom.bankapi.client.BankClients.LoanClient;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.Customer;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.CustomerInfo;
import org.springframework.stereotype.Service;

import java.util.concurrent.StructuredTaskScope;

/**
 * Демонстрира основния Structured Concurrency use case (конкретен сценарий) в лабораторията.
 *
 * <p>След като вече имаме {@link Customer}, трите downstream операции
 * (операциите към услуги/данни, които текущият компонент извиква) са независими:
 * accounts, loans и credit score. Нито една от тях не се нуждае от резултата на другата,
 * затова е безопасно да ги стартираме concurrently (едновременно).</p>
 *
 * <p>Важно е да не приемаме, че всяка последователност от три HTTP calls трябва автоматично
 * да стане concurrent. Fork-ваме само работа, за която dependency graph-ът
 * (графът на зависимостите между операциите) го позволява.</p>
 *
 * <p>README: разделите „Structured Concurrency“ и „StructuredTaskScope стъпка по стъпка“.
 * Подробна локална справка за Java 25 Joiner policy-тата има и в
 * {@code project-loom/JOINER-POLICIES.md}.</p>
 *
 * @see <a href="https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.Joiner.html">
 *     Java 25 StructuredTaskScope.Joiner API</a>
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
         * open() създава lexical scope (обхват, чието начало и край се виждат директно в кода),
         * който е owner (компонентът, отговорен за lifecycle-а) на child задачите по-долу.
         *
         * Това е една от най-важните идеи на Structured Concurrency:
         * lifetime-ът (времето на живот) на concurrent работата е видим от самата структура на кода.
         * Scope-ът започва тук и завършва при края на try блока.
         *
         * ВАЖНО: StructuredTaskScope не предлага само една policy (политика за завършване).
         * В Java 25 основните built-in Joiner варианти са:
         *
         * 1) awaitAllSuccessfulOrThrow()
         *    - всички subtasks трябва да завършат успешно;
         *    - при failure (грешка) на една задача scope-ът cancel-ва ненужната останала работа;
         *    - join() не връща самите резултати, а след него четем отделните Subtask#get();
         *    - подходящо е, когато child задачите могат да връщат РАЗЛИЧНИ типове резултат.
         *
         * 2) allSuccessfulOrThrow()
         *    - отново всички subtasks трябва да успеят и при failure се cancel-ват останалите;
         *    - join() връща Stream<Subtask<T>>;
         *    - удобно е, когато задачите връщат ЕДИН И СЪЩ тип и искаме всички резултати като колекция.
         *
         * 3) anySuccessfulResultOrThrow()
         *    - достатъчен е първият УСПЕШЕН резултат;
         *    - след него останалата работа вече не е нужна и може да бъде cancel-ната;
         *    - join() връща директно резултата T;
         *    - хвърля failure само ако всички subtasks се провалят.
         *    Използваме точно тази policy по-навътре в CreditScoreClient, където два provider-а
         *    се състезават и ни е достатъчен първият успешен credit score.
         *
         * 4) awaitAll()
         *    - чака всички subtasks независимо дали някоя fail-ва;
         *    - не cancel-ва scope-а само заради failure и join() не хвърля заради child failure;
         *    - полезно е например при независими side effects (странични ефекти) или когато
         *      искаме след края сами да разгледаме кои операции са успели и кои са се провалили.
         *
         * 5) allUntil(predicate)
         *    - чака, докато всички приключат ИЛИ predicate-ът (условието) каже „имам достатъчно“;
         *    - тогава може да short-circuit-не (приключи по-рано) и да cancel-не останалата работа;
         *    - полезно е за custom условие, което built-in policy-тата по-горе не покриват.
         *
         * Може да се напише и custom Joiner, когато тези стратегии не са достатъчни, но policy-то
         * трябва да описва общ concurrency behavior, а не да се превръща в място за business logic.
         *
         * Конкретно StructuredTaskScope.open() БЕЗ Joiner е еквивалентно на
         * awaitAllSuccessfulOrThrow(). Избираме го тук, защото CustomerInfo е валиден само ако
         * имаме И accounts, И loans, И credit score. Един успешен резултат не компенсира липсващите
         * други два. Освен това трите резултата са от различни типове, затова след join() четем
         * отделните handles чрез accountsTask.get(), loansTask.get() и creditScoreTask.get().
         *
         * Подробно: project-loom/JOINER-POLICIES.md
         * Official Java 25 API:
         * https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.Joiner.html
         */
        try (var scope = StructuredTaskScope.open()) {

            /*
             * fork() НЕ означава просто „пусни Runnable някъде“.
             * Казваме, че тази task е child (дъщерна задача) на текущата structured операция.
             *
             * Тези три calls са предимно blocking HTTP I/O (операции, които прекарват време
             * в чакане на външен отговор). Докато един virtual thread чака downstream отговор,
             * carrier thread (platform нишката, върху която JVM в момента изпълнява virtual thread-а)
             * може да изпълнява друга работа.
             *
             * Всяка променлива е Subtask<T> handle (дръжка към дъщерната задача).
             * Резултатът ще се прочете след join().
             */
            var accountsTask = scope.fork(() -> accountClient.getAccounts(customer.id()));
            var loansTask = scope.fork(() -> loanClient.getLoans(customer.id()));
            var creditScoreTask = scope.fork(() -> creditScoreClient.getFirstSuccessfulScore(customer.id()));

            /*
             * join() е structural join point-ът
             * (ясната точка, в която паралелните пътища отново се събират).
             *
             * До този ред трите child операции могат да вървят едновременно.
             * От този ред надолу business logic-ът изисква техните резултати.
             *
             * Това е по-лесно за reasoning (проследяване и разбиране на поведението)
             * от разпръснати Future#get()/join() calls, защото имаме ясно място,
             * където паралелните пътища отново се събират.
             */
            scope.join();

            /*
             * След успешен join() required subtasks (задължителните дъщерни задачи)
             * са приключили успешно според policy-то (правилото за завършване) на този scope.
             * Събираме резултатите обратно в един нормален domain object.
             *
             * Оттук нататък кодът отново е обикновен sequential business code
             * (последователна бизнес логика).
             */
            return new CustomerInfo(
                    accountsTask.get(),
                    loansTask.get(),
                    creditScoreTask.get());

        } catch (InterruptedException e) {
            /*
             * InterruptedException е cooperative cancellation signal
             * (сигнал за прекратяване, който кодът трябва доброволно да обработи).
             *
             * Не трябва да го „изяждаме“. Когато го превръщаме в unchecked/application
             * exception, възстановяваме interrupt flag-а, за да може код по-нагоре по
             * call stack-а (веригата от извикани методи) да разбере, че thread-ът е бил
             * поискан за прекратяване.
             */
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Customer info loading was interrupted", e);
        }
    }
}
