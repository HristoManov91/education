package bg.hristomanov.education.loom.bankapi.context;

import bg.hristomanov.education.loom.bankapi.context.RequestContext.RequestMetadata;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.StructuredTaskScope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestContextTest {

    @Test
    void scopedValueExistsOnlyInsideItsDynamicScope() {
        var requestId = UUID.randomUUID();

        assertFalse(RequestContext.isBound());

        var observed = RequestContext.call(
                new RequestMetadata(requestId),
                () -> RequestContext.current().requestId());

        assertEquals(requestId, observed);
        assertFalse(RequestContext.isBound());
        assertThrows(IllegalStateException.class, RequestContext::current);
    }

    @Test
    void structuredChildThreadInheritsScopedValue() throws Exception {
        var requestId = UUID.randomUUID();

        var observed = RequestContext.call(new RequestMetadata(requestId), () -> {
            try (var scope = StructuredTaskScope.open()) {
                var child = scope.fork(() -> RequestContext.current().requestId());
                scope.join();
                return child.get();
            }
        });

        assertEquals(requestId, observed);
        assertFalse(RequestContext.isBound());
    }
}
