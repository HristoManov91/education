package bg.hristomanov.education.performance.benchmarks;

import bg.hristomanov.education.ffi.ConfinedArenaAllocationLab;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * JDK 28 EA native pool boundary experiment: <=64 bytes vs >64 bytes.
 * Не се очаква runtime cost на JDK 25/27 да съвпада с JDK 28.
 */
public class ConfinedArenaPoolBenchmark {

    @State(Scope.Thread)
    public static class Fixture {
        @Param({"8", "32", "64", "128"})
        public int bytes;
        private int value = 1;
    }

    @Benchmark
    public int confinedArenaAllocateWriteRead(Fixture fixture) {
        return ConfinedArenaAllocationLab.allocateWriteRead(fixture.bytes, ++fixture.value);
    }
}
