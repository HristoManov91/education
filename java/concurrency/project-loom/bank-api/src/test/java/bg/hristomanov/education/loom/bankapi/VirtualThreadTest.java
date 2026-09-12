package bg.hristomanov.education.loom.bankapi;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Минимална executable проверка на самата Java Virtual Threads семантика.
 *
 * <p>Тестът не проверява Spring Boot configuration-а. Той изолира по-ниското ниво:
 * Java 25 действително създава virtual thread и кодът вътре може да го разпознае чрез
 * {@link Thread#isVirtual()}.</p>
 */
class VirtualThreadTest {

    @Test
    void javaCanRunAUnitOfWorkOnAVirtualThread() throws InterruptedException {
        /*
         * AtomicBoolean използваме не заради business concurrency, а за безопасно да върнем
         * наблюдаваната стойност от child thread към test thread-а.
         */
        AtomicBoolean observedVirtualThread = new AtomicBoolean(false);

        /*
         * startVirtualThread(...) е най-директният Java API за:
         * 1) създаване на virtual thread;
         * 2) незабавното му стартиране.
         *
         * Методът връща Thread. Изписваме типа explicit, за да се вижда директно API contract-ът.
         * Това не е pool: създаваме конкретен thread за конкретната test task.
         */
        Thread thread = Thread.startVirtualThread(
                () -> observedVirtualThread.set(Thread.currentThread().isVirtual()));

        /*
         * Test thread-ът трябва да изчака child thread-а, преди да проверим резултата.
         * Иначе assert-ът може да се изпълни преди lambda-та.
         */
        thread.join();

        assertTrue(observedVirtualThread.get());
    }
}
