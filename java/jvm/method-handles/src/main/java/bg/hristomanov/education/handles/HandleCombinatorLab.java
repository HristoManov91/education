package bg.hristomanov.education.handles;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Builds a type-safe dispatch graph once via MethodHandles.guardWithTest.
 * This is a teaching case, not an argument to replace simple Java if/else.
 */
public final class HandleCombinatorLab {

    private static final MethodHandle DISCOUNT_POLICY = buildPolicy();

    private HandleCombinatorLab() {
    }

    private static MethodHandle buildPolicy() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType intToBool = MethodType.methodType(boolean.class, int.class);
            MethodType intToInt = MethodType.methodType(int.class, int.class);
            MethodHandle condition = lookup.findStatic(
                    HandleCombinatorLab.class, "isLarge", intToBool);
            MethodHandle discounted = lookup.findStatic(
                    HandleCombinatorLab.class, "discount", intToInt);
            MethodHandle unchanged = lookup.findStatic(
                    HandleCombinatorLab.class, "unchanged", intToInt);
            return MethodHandles.guardWithTest(condition, discounted, unchanged);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    public static int linkedPrice(int grossCents) throws Throwable {
        if (grossCents < 0) {
            throw new IllegalArgumentException("Price must not be negative");
        }
        return (int) DISCOUNT_POLICY.invokeExact(grossCents);
    }

    public static int directPrice(int grossCents) {
        if (grossCents < 0) {
            throw new IllegalArgumentException("Price must not be negative");
        }
        return grossCents >= 1_000 ? Math.subtractExact(grossCents, 100) : grossCents;
    }

    private static boolean isLarge(int price) {
        return price >= 1_000;
    }

    private static int discount(int price) {
        return Math.subtractExact(price, 100);
    }

    private static int unchanged(int price) {
        return price;
    }
}
