package bg.hristomanov.education.handles;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HandleInvocationLabTest {

    private static final Point SOURCE = new Point(1, 2);

    @Test
    void directReflectionExactAdaptiveAndColdLinkProduceSameBusinessValue() throws Throwable {
        Point expected = new Point(4, 6);
        assertEquals(expected, HandleInvocationLab.direct(SOURCE, 3, 4));
        assertEquals(expected, HandleInvocationLab.reflection(SOURCE, 3, 4));
        assertEquals(expected, HandleInvocationLab.linkedHandle(SOURCE, 3, 4));
        assertEquals(expected, HandleInvocationLab.adaptiveHandle(SOURCE, 3, 4));
        assertEquals(expected, HandleInvocationLab.lookupEachTime(SOURCE, 3, 4));
    }

    @Test
    void virtualHandleTypeIncludesTheReceiverAndExactReturnType() throws Throwable {
        MethodHandle handle = HandleInvocationLab.linkTranslate();
        assertEquals(MethodType.methodType(
                Point.class, Point.class, int.class, int.class), handle.type());
        assertEquals(handle.type(), HandleInvocationLab.linkedType());
        assertEquals(new Point(4, 6), (Point) handle.invokeExact(SOURCE, 3, 4));
    }

    @Test
    void invokeExactRejectsObjectAsReturnEvenIfPointIsAssignableToObject() {
        assertThrows(WrongMethodTypeException.class,
                () -> HandleInvocationLab.invalidExactReturnType(SOURCE));
    }

    @Test
    void reflectionWrapsBusinessErrorButHandlePropagatesIt() {
        Point overflow = new Point(Integer.MAX_VALUE, 0);
        InvocationTargetException reflected = assertThrows(
                InvocationTargetException.class,
                () -> HandleInvocationLab.reflection(overflow, 1, 0));
        assertInstanceOf(ArithmeticException.class, reflected.getCause());
        assertThrows(ArithmeticException.class,
                () -> HandleInvocationLab.linkedHandle(overflow, 1, 0));
    }

    @Test
    void missingMethodFailsAtLinkageNotRepeatedExecution() {
        assertThrows(NoSuchMethodException.class,
                () -> java.lang.invoke.MethodHandles.lookup().findVirtual(
                        Point.class, "doesNotExist", MethodType.methodType(Point.class)));
    }
}
