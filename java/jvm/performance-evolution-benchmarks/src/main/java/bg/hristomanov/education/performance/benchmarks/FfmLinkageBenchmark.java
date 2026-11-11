package bg.hristomanov.education.performance.benchmarks;

import bg.hristomanov.education.ffi.NativeCStringLength;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * FFM linkage vs execution cost: run only where native strlen is supported,
 * with --enable-native-access=ALL-UNNAMED. Do NOT compare wall-clock numbers
 * from different JDK/OS/ABI environments as interchangeable.
 */
public class FfmLinkageBenchmark {

    @State(Scope.Thread)
    public static class Fixture {
        @Param({"ascii", "utf8"})
        public String scenario;
        public String text;

        @Setup(Level.Trial)
        public void setUp() {
            if (!NativeCStringLength.isSupported()) {
                throw new IllegalStateException("This native strlen ABI is not supported");
            }
            text = "utf8".equals(scenario) ? "A😊 and Unicode" : "FFM method handle";
            // Ensure first-time lazy linkage is not counted as per-call cost.
            NativeCStringLength.utf8Length(text);
        }
    }

    @Benchmark
    public long cachedStrlen(Fixture state) {
        return NativeCStringLength.utf8Length(state.text);
    }

    @Benchmark
    public long relinkStrlenEveryCall(Fixture state) {
        return NativeCStringLength.utf8LengthWithoutCacheForComparison(state.text);
    }
}
