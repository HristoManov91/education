package bg.hristomanov.education.handles;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

/**
 * Same domain operation via direct call, Core Reflection and Method Handles.
 *
 * <p>Reflective Method.invoke wraps target exceptions in InvocationTargetException;
 * MethodHandle.invokeExact propagates them without that wrapper. These APIs
 * do not make a universally valid speed ranking: performance is JDK/JIT dependent.
 */
public final class HandleInvocationLab {

    private static final MethodType TRANSLATE_SIGNATURE =
            MethodType.methodType(Point.class, int.class, int.class);

    private static final Method REFLECTED_TRANSLATE;
    private static final MethodHandle PRELINKED_TRANSLATE;

    static {
        try {
            REFLECTED_TRANSLATE = Point.class.getMethod("translate", int.class, int.class);
            PRELINKED_TRANSLATE = linkTranslate();
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private HandleInvocationLab() {
    }

    public static Point direct(Point point, int dx, int dy) {
        return point.translate(dx, dy);
    }

    public static Point reflection(Point point, int dx, int dy)
            throws ReflectiveOperationException {
        return (Point) REFLECTED_TRANSLATE.invoke(point, dx, dy);
    }

    public static Point linkedHandle(Point point, int dx, int dy) throws Throwable {
        // invokeExact checks (Point, int, int) -> Point, including return type.
        return (Point) PRELINKED_TRANSLATE.invokeExact(point, dx, dy);
    }

    public static Point adaptiveHandle(Point point, int dx, int dy) throws Throwable {
        // invoke permits conversions such as unboxing Integer -> int.
        return (Point) PRELINKED_TRANSLATE.invoke(
                point, Integer.valueOf(dx), Integer.valueOf(dy));
    }

    public static Point lookupEachTime(Point point, int dx, int dy) throws Throwable {
        // Deliberately naive path: performs symbolic member lookup per call.
        MethodHandle handle = linkTranslate();
        return (Point) handle.invokeExact(point, dx, dy);
    }

    public static Object invalidExactReturnType(Point point) throws Throwable {
        // The call site expects Object instead of Point: must throw.
        return (Object) PRELINKED_TRANSLATE.invokeExact(point, 3, 4);
    }

    public static MethodHandle linkTranslate() throws ReflectiveOperationException {
        return MethodHandles.lookup()
                .findVirtual(Point.class, "translate", TRANSLATE_SIGNATURE);
    }

    public static MethodType linkedType() {
        return PRELINKED_TRANSLATE.type();
    }
}
