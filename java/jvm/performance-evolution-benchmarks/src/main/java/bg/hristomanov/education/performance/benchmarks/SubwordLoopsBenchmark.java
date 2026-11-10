package bg.hristomanov.education.performance.benchmarks;

import bg.hristomanov.education.vector.SubwordLoops;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * JDK 25/27 C2 SuperWord сравнение: scalar Java код с widening/narrowing.
 * JMH резултат сам по себе си не доказва кои SIMD инструкции са генерирани.
 */
public class SubwordLoopsBenchmark {

    @State(Scope.Thread)
    public static class Fixture {
        @Param({"16", "65", "4096"})
        public int length;

        private int[] ints;
        private short[] shorts;

        @Setup(Level.Trial)
        public void setUp() {
            ints = new int[length];
            shorts = new short[length];
            for (int index = 0; index < length; index++) {
                ints[index] = index * 101 - 400;
                shorts[index] = (short) (index * 1101 - 32768);
            }
        }
    }

    @Benchmark
    public byte[] narrowInts(Fixture fixture) {
        return SubwordLoops.narrowInts(fixture.ints);
    }

    @Benchmark
    public int[] widenShorts(Fixture fixture) {
        return SubwordLoops.widenShorts(fixture.shorts);
    }
}
