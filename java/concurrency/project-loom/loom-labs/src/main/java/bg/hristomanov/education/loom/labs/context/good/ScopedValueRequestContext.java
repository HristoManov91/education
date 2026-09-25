package bg.hristomanov.education.loom.labs.context.good;

import java.lang.ScopedValue;
import java.util.concurrent.StructuredTaskScope;

/**
 * GOOD пример за immutable request context, който трябва да се вижда
 * надолу по structured concurrency tree-а.
 */
public final class ScopedValueRequestContext {

    private static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

    private ScopedValueRequestContext() {
    }

    public static String readFromStructuredChild(String requestId)
            throws InterruptedException {

        /*
         * Binding-ът има bounded lifetime: съществува само докато call(...) се изпълнява.
         * StructuredTaskScope се отваря вътре в този binding и затова child thread-ът
         * наследява същата ScopedValue стойност.
         */
        return ScopedValue.where(REQUEST_ID, requestId).call(() -> {
            try (StructuredTaskScope<String, Void> scope =
                         StructuredTaskScope.open()) {

                StructuredTaskScope.Subtask<String> childTask =
                        scope.fork(REQUEST_ID::get);

                scope.join();
                return childTask.get();
            }
        });
    }

    public static boolean isBound() {
        return REQUEST_ID.isBound();
    }
}
