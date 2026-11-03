package bg.hristomanov.education.performance.benchmarks;

import bg.hristomanov.education.vector.ScalarDotProducts;
import bg.hristomanov.education.vector.VectorDotProducts;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/** Each benchmark scores the same arrays; return values remain observable to JMH. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = {"--add-modules=jdk.incubator.vector"})
public class DotProductBenchmark {
    @Param({"16", "256", "1024", "8192"})
    public int dimensions;

    private float[] left;
    private float[] right;

    @Setup(Level.Trial)
    public void setup() {
        left = new float[dimensions];
        right = new float[dimensions];
        Random random = new Random(0xC0FFEE);
        for (int i = 0; i < dimensions; i++) {
            left[i] = 2f * random.nextFloat() - 1f;
            right[i] = 2f * random.nextFloat() - 1f;
        }
    }

    @Benchmark public float scalar() { return ScalarDotProducts.simple(left, right); }
    @Benchmark public float scalarIlp4() { return ScalarDotProducts.unrolled4(left, right); }
    @Benchmark public float scalarFma4() { return ScalarDotProducts.fused4(left, right); }
    @Benchmark public float vectorMulAdd() { return VectorDotProducts.multiplyAdd(left, right); }
    @Benchmark public float vectorFma() { return VectorDotProducts.fusedMultiplyAdd(left, right); }
    @Benchmark public float vectorSmallInputFallback() { return VectorDotProducts.smallInputFallback(left, right); }
}
