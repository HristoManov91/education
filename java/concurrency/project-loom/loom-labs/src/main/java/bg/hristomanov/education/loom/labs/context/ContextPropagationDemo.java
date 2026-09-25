package bg.hristomanov.education.loom.labs.context;

import bg.hristomanov.education.loom.labs.context.bad.ThreadLocalRequestContext;
import bg.hristomanov.education.loom.labs.context.good.ScopedValueRequestContext;

/**
 * Минимална executable демонстрация на разликата между ThreadLocal и ScopedValue
 * при StructuredTaskScope.
 */
public final class ContextPropagationDemo {

    private ContextPropagationDemo() {
    }

    public static void main(String[] args) throws InterruptedException {
        String requestId = "request-42";

        String threadLocalChildValue =
                ThreadLocalRequestContext.readFromStructuredChild(requestId);

        String scopedValueChildValue =
                ScopedValueRequestContext.readFromStructuredChild(requestId);

        System.out.println("Parent requestId:        " + requestId);
        System.out.println("ThreadLocal in child:   " + threadLocalChildValue);
        System.out.println("ScopedValue in child:   " + scopedValueChildValue);
        System.out.println("ScopedValue after call: bound="
                + ScopedValueRequestContext.isBound());
    }
}
