package bg.hristomanov.education.loom.bankapi.context;

import java.lang.ScopedValue;
import java.util.UUID;

/**
 * Малък wrapper около {@link ScopedValue}, използван за request-scoped immutable metadata.
 *
 * <p>Защо не подаваме requestId като parameter навсякъде? Това би било напълно коректно,
 * но за cross-cutting metadata (correlation id, trace id, tenant id и т.н.) може да замърси
 * method signatures на много нива, които бизнес логиката не интересува.</p>
 *
 * <p>Защо не ThreadLocal? ScopedValue е по-подходящ за one-way context propagation:
 * caller-ът bind-ва стойността за ограничен dynamic scope, callees я четат, а след края
 * на scope-а binding-ът автоматично изчезва. Не разчитаме на ръчен remove() cleanup.</p>
 *
 * <p>Особено важна връзка с Project Loom: StructuredTaskScope child threads наследяват
 * текущите ScopedValue bindings, което позволява един requestId да се вижда в целия
 * structured task tree без ръчно копиране.</p>
 */
public final class RequestContext {

    /*
     * ScopedValue object-ът е static identity/key, а НЕ място с една глобална mutable стойност.
     * Реалната стойност зависи от текущия dynamic scope на execution-а.
     *
     * Два едновременни HTTP requests могат да bind-нат различни RequestMetadata стойности
     * към същия METADATA key без да си пречат.
     */
    private static final ScopedValue<RequestMetadata> METADATA = ScopedValue.newInstance();

    private RequestContext() {
        // Utility/context holder: не искаме instances.
    }

    /**
     * Изпълнява операцията с временно bound request metadata.
     *
     * <p>Binding-ът важи само докато {@code operation} се изпълнява. Това lexical/dynamic
     * ограничение прави lifecycle-а много по-ясен от mutable ThreadLocal state.</p>
     */
    public static <T, X extends Throwable> T call(
            RequestMetadata metadata,
            ScopedValue.CallableOp<T, X> operation) throws X {

        return ScopedValue.where(METADATA, metadata).call(operation);
    }

    /**
     * Връща metadata за текущия execution scope.
     *
     * <p>Умишлено fail-ваме, ако method-ът бъде извикан извън request scope. Това помага
     * да открием неправилен lifecycle веднага, вместо тихо да логваме null requestId.</p>
     */
    public static RequestMetadata current() {
        return METADATA.orElseThrow(
                () -> new IllegalStateException("RequestContext is not bound to the current scope"));
    }

    /**
     * Полезно основно за тестове/diagnostics: позволява да докажем, че binding-ът е
     * приключил след излизане от {@link #call(RequestMetadata, ScopedValue.CallableOp)}.
     */
    public static boolean isBound() {
        return METADATA.isBound();
    }

    /**
     * Immutable metadata object. Record-ът е подходящ, защото context-ът трябва да бъде
     * read-only след bind-ването му; ScopedValue е замислен за one-way sharing на data.
     */
    public record RequestMetadata(UUID requestId) {
    }
}
