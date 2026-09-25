package bg.hristomanov.education.loom.labs.context.bad;

import java.util.concurrent.StructuredTaskScope;

/**
 * BAD/naive пример за request context, когато очакваме той автоматично
 * да се вижда в structured child thread.
 *
 * <p>ThreadLocal е стойност, асоциирана с конкретен Thread. Фактът, че child task-ът
 * е structured и работи на virtual thread, не означава, че обикновеният ThreadLocal
 * на parent thread-а автоматично се копира в него.</p>
 */
public final class ThreadLocalRequestContext {

    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private ThreadLocalRequestContext() {
    }

    public static String readFromStructuredChild(String requestId)
            throws InterruptedException {

        REQUEST_ID.set(requestId);

        try {
            try (StructuredTaskScope<String, Void> scope =
                         StructuredTaskScope.open()) {

                StructuredTaskScope.Subtask<String> childTask =
                        scope.fork(REQUEST_ID::get);

                scope.join();

                /*
                 * Резултатът е null: child thread-ът има собствен ThreadLocal state.
                 * Това е лесно да се пропусне, ако мислим за ThreadLocal като за
                 * "request global", вместо като за thread-local state.
                 */
                return childTask.get();
            }
        } finally {
            /*
             * ThreadLocal има manual lifecycle. Ако го използваме за request context,
             * трябва дисциплинирано да го почистим.
             */
            REQUEST_ID.remove();
        }
    }
}
