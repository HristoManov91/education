package bg.hristomanov.education.handles;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatException;
import java.lang.invoke.StringConcatFactory;

/**
 * Demonstrates an explicit StringConcatFactory bootstrap and its cached
 * CallSite target. The plain + expression is the normal production solution.
 */
public final class StringConcatLab {

    private static final MethodHandle TARGET = linkConcatTarget();

    private StringConcatLab() {
    }

    private static MethodHandle linkConcatTarget() {
        try {
            String recipe = "order=" + (char) 1 + " qty=" + (char) 1;
            CallSite linked = StringConcatFactory.makeConcatWithConstants(
                    MethodHandles.lookup(),
                    "makeConcatWithConstants",
                    MethodType.methodType(String.class, String.class, int.class),
                    recipe);
            return linked.getTarget();
        } catch (StringConcatException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    public static String plainJava(String sku, int quantity) {
        return "order=" + sku + " qty=" + quantity;
    }

    public static String prelinkedCallSite(String sku, int quantity) throws Throwable {
        return (String) TARGET.invokeExact(sku, quantity);
    }

    public static MethodType linkedType() {
        return TARGET.type();
    }
}
