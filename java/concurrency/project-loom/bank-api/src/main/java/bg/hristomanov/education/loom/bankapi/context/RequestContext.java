package bg.hristomanov.education.loom.bankapi.context;

import java.lang.ScopedValue;
import java.util.UUID;

/**
 * ScopedValue е подходящ за request metadata, което се предава само надолу по call stack-а.
 * За разлика от ThreadLocal, binding-ът има ясен и ограничен lifetime.
 */
public final class RequestContext {

    private static final ScopedValue<RequestMetadata> METADATA = ScopedValue.newInstance();

    private RequestContext() {
    }

    public static <T, X extends Throwable> T call(
            RequestMetadata metadata,
            ScopedValue.CallableOp<T, X> operation) throws X {
        return ScopedValue.where(METADATA, metadata).call(operation);
    }

    public static RequestMetadata current() {
        return METADATA.orElseThrow(
                () -> new IllegalStateException("RequestContext is not bound to the current scope"));
    }

    public static boolean isBound() {
        return METADATA.isBound();
    }

    public record RequestMetadata(UUID requestId) {
    }
}
