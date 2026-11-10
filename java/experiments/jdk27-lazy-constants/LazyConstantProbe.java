import java.lang.LazyConstant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Self-checking Java 27 preview example; excluded from the Java 25 Maven reactor. */
public class LazyConstantProbe {
    private static final AtomicInteger CONSTRUCTION_COUNT = new AtomicInteger();
    private static final LazyConstant<List<String>> LOOKUP = LazyConstant.of(() -> {
        CONSTRUCTION_COUNT.incrementAndGet();
        return List.of("one", "two");
    });

    public static void main(String[] args) throws Exception {
        if (CONSTRUCTION_COUNT.get() != 0) throw new AssertionError("Not lazy");
        List<List<String>> seen = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<List<String>>> concurrent = new ArrayList<>();
            for (int i = 0; i < 32; i++) concurrent.add(LOOKUP::get);
            for (var result : executor.invokeAll(concurrent)) {
                seen.add(result.get());
            }
        }
        List<String> values = LOOKUP.get();
        if (CONSTRUCTION_COUNT.get() != 1 || seen.stream().anyMatch(value -> value != values)) {
            throw new AssertionError("Concurrent callers did not observe one initialized value");
        }

        AtomicInteger failures = new AtomicInteger();
        LazyConstant<String> broken = LazyConstant.of(() -> {
            failures.incrementAndGet();
            throw new IllegalStateException("init failed");
        });
        for (int i = 0; i < 2; i++) {
            try {
                broken.get();
                throw new AssertionError("Expected sticky initialization error");
            } catch (NoSuchElementException expected) {
                // In Java 27 a failure transitions the constant to a permanent error state.
            }
        }
        if (failures.get() != 1) throw new AssertionError("Failed constant was recomputed");
        System.out.println("LazyConstantProbe PASS: lazy, concurrent singleton, sticky failure");
    }
}
