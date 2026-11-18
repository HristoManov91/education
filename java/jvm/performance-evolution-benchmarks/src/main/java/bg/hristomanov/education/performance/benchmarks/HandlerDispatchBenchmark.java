package bg.hristomanov.education.performance.benchmarks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import bg.hristomanov.education.performance.quarkus.HandlerDispatchLab;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures strategy overhead for the same output. NOT a Jackson or HTTP benchmark.
 * Report distributions, forks, CPU and GC when interpreting results.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class HandlerDispatchBenchmark {
    @State(Scope.Thread)
    public static class BenchmarkState {
        @Param({"1", "3", "9"})
        public int chainLength;

        HandlerDispatchLab.Chain chain;

        @Setup(Level.Trial)
        public void setup() {
            List<HandlerDispatchLab.Handler> handlers = new ArrayList<>();
            for (int index = 0; index < chainLength; index++) {
                switch (index % 3) {
                    case 0 -> handlers.add(new HandlerDispatchLab.Add(2));
                    case 1 -> handlers.add(new HandlerDispatchLab.Multiply(3));
                    default -> handlers.add(new HandlerDispatchLab.Xor(7));
                }
            }
            chain = new HandlerDispatchLab.Chain(handlers);
        }
    }

    @Benchmark
    public int virtualCall(BenchmarkState state) {
        return state.chain.viaVirtual(17);
    }

    @Benchmark
    public int instanceofAtRuntime(BenchmarkState state) {
        return state.chain.viaInstanceof(17);
    }

    @Benchmark
    public int preclassifiedSwitch(BenchmarkState state) {
        return state.chain.viaPreclassified(17);
    }
}
