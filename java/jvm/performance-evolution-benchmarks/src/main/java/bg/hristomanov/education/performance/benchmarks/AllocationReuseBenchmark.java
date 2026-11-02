package bg.hristomanov.education.performance.benchmarks;

import bg.hristomanov.education.performance.runtime.MutableEvent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Изолиран microbenchmark: референциите се редуват и event-ът е наблюдаем
 * чрез Blackhole, за да не се елиминира тривиално allocation-ът.
 * Reuse e thread-confined; в реални HTTP отговори object identity е важна.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class AllocationReuseBenchmark {

    @State(Scope.Thread)
    public static class EventState {
        private final String[] keys = {"id-a", "id-b", "id-c", "id-d"};
        private final String[] details = {"created", "updated", "approved", "sent"};
        private final MutableEvent reusable = new MutableEvent("initial", "initial", 0);
        private int cursor;

        public int nextIndex() {
            int index = cursor & 3;
            cursor++;
            return index;
        }
    }

    @Benchmark
    public void allocateFresh(EventState state, Blackhole blackhole) {
        int index = state.nextIndex();
        MutableEvent event = new MutableEvent(state.keys[index], state.details[index], index);
        blackhole.consume(event);
    }

    @Benchmark
    public void overwriteExisting(EventState state, Blackhole blackhole) {
        int index = state.nextIndex();
        state.reusable.overwrite(state.keys[index], state.details[index], index);
        blackhole.consume(state.reusable);
    }
}
