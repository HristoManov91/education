package bg.hristomanov.education.loom.bankapi.context;

import bg.hristomanov.education.loom.bankapi.context.RequestContext.RequestMetadata;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.StructuredTaskScope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Тези тестове доказват две ключови свойства на ScopedValue, които са важни за тази лаборатория:
 *
 * <ol>
 *   <li>binding-ът има bounded lifetime;</li>
 *   <li>StructuredTaskScope child thread наследява binding-а.</li>
 * </ol>
 *
 * <p>Именно тези свойства са причината ScopedValue да е подходящ за request metadata.</p>
 */
class RequestContextTest {

    @Test
    void scopedValueExistsOnlyInsideItsDynamicScope() {
        UUID requestId = UUID.randomUUID();

        // Преди да отворим scope няма случайно останал context от предишна операция.
        assertFalse(RequestContext.isBound());

        /*
         * call(...) bind-ва metadata само за времето на lambda-та.
         * Вътре current() трябва да вижда точно requestId, който caller-ът е bind-нал.
         */
        UUID observed = RequestContext.call(
                new RequestMetadata(requestId),
                () -> RequestContext.current().requestId());

        assertEquals(requestId, observed);

        /*
         * След края на call(...) binding-ът вече не съществува.
         * Това е важната bounded-lifetime гаранция и причината да няма ThreadLocal-style remove().
         */
        assertFalse(RequestContext.isBound());
        assertThrows(IllegalStateException.class, RequestContext::current);
    }

    @Test
    void structuredChildThreadInheritsScopedValue() throws Exception {
        UUID requestId = UUID.randomUUID();

        UUID observed = RequestContext.call(new RequestMetadata(requestId), () -> {
            /*
             * Scope-ът се отваря ДОКАТО ScopedValue binding-ът е active.
             * Child task-ът, fork-нат от този StructuredTaskScope, наследява binding-а.
             *
             * StructuredTaskScope<Object, Void> е explicit type-ът на default open():
             * scope-ът приема subtasks с различни result types, а join() връща Void/null.
             */
            try (StructuredTaskScope<Object, Void> scope = StructuredTaskScope.open()) {
                StructuredTaskScope.Subtask<UUID> child =
                        scope.fork(() -> RequestContext.current().requestId());

                // join() гарантира, че child task-ът е приключил, преди да вземем резултата.
                scope.join();
                return child.get();
            }
        });

        // Child thread-ът е видял същия requestId, без да го подадем като method parameter.
        assertEquals(requestId, observed);

        // И отново: след outer scope-а няма leaked request context.
        assertFalse(RequestContext.isBound());
    }
}
