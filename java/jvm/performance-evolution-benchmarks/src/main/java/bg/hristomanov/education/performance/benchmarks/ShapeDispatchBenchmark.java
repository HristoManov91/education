package bg.hristomanov.education.performance.benchmarks;

import bg.hristomanov.education.performance.runtime.ManualShapeDispatch;
import bg.hristomanov.education.performance.runtime.ShapeDispatch;
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

import java.util.concurrent.TimeUnit;

/**
 * Сравнява virtual dispatch и ръчни type checks за еднакъв бизнес резултат.
 * JMH измерва цялата операция (loop + access + dispatch), не чистата цена на vtable.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class ShapeDispatchBenchmark {

    @Param({"1", "2", "3"})
    public int distinctTypes;

    private ShapeDispatch.Shape[] shapes;

    @Setup(Level.Trial)
    public void setup() {
        shapes = ShapeDispatch.sampleShapes(distinctTypes);
    }

    @Benchmark
    public double virtualDispatch() {
        return ShapeDispatch.sumDoubleAreas(shapes, 128);
    }

    @Benchmark
    public double explicitTypeChecks() {
        double total = 0.0;
        for (int round = 0; round < 128; round++) {
            for (ShapeDispatch.Shape shape : shapes) {
                total += ManualShapeDispatch.doubleArea(shape);
            }
        }
        return total;
    }
}
