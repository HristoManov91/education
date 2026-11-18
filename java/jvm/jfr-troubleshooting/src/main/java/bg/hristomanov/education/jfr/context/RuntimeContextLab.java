package bg.hristomanov.education.jfr.context;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * No Spring context or external IO: creates two genuinely nested JFR events
 * on one thread, and a deterministic checksum. This is NOT a benchmark.
 */
public final class RuntimeContextLab {
    private static final Pattern SYNTHETIC_TRACE = Pattern.compile("lab-[a-z0-9-]{1,32}");
    private static final int MAX_ITERATIONS = 250_000;

    public long run(String traceId, int iterations) {
        Objects.requireNonNull(traceId, "traceId");
        if (!SYNTHETIC_TRACE.matcher(traceId).matches()) {
            throw new IllegalArgumentException("Only synthetic IDs starting with lab- are allowed");
        }
        if (iterations < 1 || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("iterations must be between 1 and 250000");
        }

        RuntimeTraceEvent scope = new RuntimeTraceEvent();
        scope.traceId = traceId;
        scope.operation = "synthetic-cpu";
        scope.begin();
        try {
            RuntimeWorkEvent work = new RuntimeWorkEvent();
            work.phase = "checksum";
            work.iterations = iterations;
            work.begin();
            try {
                long result = 0L;
                for (int i = 1; i <= iterations; i++) {
                    result = Long.rotateLeft(result ^ (i * 31L), 3) + i;
                }
                work.checksum = result;
                return result;
            } finally {
                work.end();
                if (work.shouldCommit()) {
                    work.commit();
                }
            }
        } finally {
            scope.end();
            if (scope.shouldCommit()) {
                scope.commit();
            }
        }
    }
}
