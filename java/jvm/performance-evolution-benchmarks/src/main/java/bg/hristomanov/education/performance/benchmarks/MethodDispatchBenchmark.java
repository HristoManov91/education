package bg.hristomanov.education.performance.benchmarks;

import bg.hristomanov.education.handles.HandleInvocationLab;
import bg.hristomanov.education.handles.Point;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Compare the SAME domain operation with direct invocation, cached reflection,
 * cached MethodHandle and deliberately cold lookup (linkage included).
 *
 * <p>These are different shapes of JIT-optimizable call sites; a benchmark
 * number alone never guarantees that MethodHandle beats modern reflection.
 */
public class MethodDispatchBenchmark {

    @State(Scope.Thread)
    public static class Fixture {
        public Point point;
        public int dx = 3;
        public int dy = 4;
        public Method reflected;
        public MethodHandle linked;

        @Setup(Level.Trial)
        public void prepare() throws ReflectiveOperationException {
            point = new Point(10, 20);
            reflected = Point.class.getMethod("translate", int.class, int.class);
            linked = HandleInvocationLab.linkTranslate();
        }
    }

    @Benchmark
    public Point direct(Fixture state) {
        return state.point.translate(state.dx, state.dy);
    }

    @Benchmark
    public Point cachedReflection(Fixture state) throws ReflectiveOperationException {
        return (Point) state.reflected.invoke(state.point, state.dx, state.dy);
    }

    @Benchmark
    public Point cachedMethodHandle(Fixture state) throws Throwable {
        return (Point) state.linked.invokeExact(state.point, state.dx, state.dy);
    }

    @Benchmark
    public Point lookupAndInvokeEveryTime(Fixture state) throws Throwable {
        return HandleInvocationLab.lookupEachTime(state.point, state.dx, state.dy);
    }
}
