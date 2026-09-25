package bg.hristomanov.education.loom.labs.context;

import bg.hristomanov.education.loom.labs.context.bad.ThreadLocalRequestContext;
import bg.hristomanov.education.loom.labs.context.good.ScopedValueRequestContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContextPropagationTest {

    @Test
    void ordinaryThreadLocalIsNotAutomaticallyVisibleInStructuredChild()
            throws InterruptedException {

        String childValue =
                ThreadLocalRequestContext.readFromStructuredChild(
                        "request-42");

        assertNull(childValue);
    }

    @Test
    void scopedValueIsInheritedAndItsBindingEndsWithTheScope()
            throws InterruptedException {

        String childValue =
                ScopedValueRequestContext.readFromStructuredChild(
                        "request-42");

        assertEquals("request-42", childValue);
        assertFalse(ScopedValueRequestContext.isBound());
    }
}
