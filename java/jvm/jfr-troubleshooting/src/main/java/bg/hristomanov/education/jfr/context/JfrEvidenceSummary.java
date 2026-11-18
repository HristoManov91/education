package bg.hristomanov.education.jfr.context;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/**
 * Small allowlist-only evidence extractor for synthetic LOCAL recordings.
 * Does NOT forward raw JFR payload, trace identifiers, JVM arguments, or paths
 * to AI. For real production JFR, human privacy/security review comes first.
 *
 * Parsing is bounded by the event count; the file must be trusted and local.
 */
public final class JfrEvidenceSummary {
    private static final int MAX_EVENTS_TO_SCAN = 10_000;

    private JfrEvidenceSummary() {
    }

    public record Summary(int traceEvents, int workEvents, long workDurationNanos,
                          int scannedEvents, boolean truncated) {
        /** A stable, human-readable allowlisted report; never contains raw trace IDs. */
        public String toReviewText() {
            return String.format(Locale.ROOT,
                    "Source: synthetic local JFR lab%n"
                    + "trace_events=%d%n"
                    + "work_events=%d%n"
                    + "total_work_duration_ms=%.3f%n"
                    + "scanned_events=%d%n"
                    + "truncated=%s%n"
                    + "Interpretation: counts and duration only; no root cause or speedup inferred.%n",
                    traceEvents, workEvents, workDurationNanos / 1_000_000d,
                    scannedEvents, truncated);
        }
    }

    public static Summary analyze(Path trustedLocalRecording) throws IOException {
        Objects.requireNonNull(trustedLocalRecording, "trustedLocalRecording");
        int traces = 0;
        int work = 0;
        int scanned = 0;
        long duration = 0L;
        boolean truncated = false;

        // Read events incrementally instead of loading an unbounded entire recording.
        try (RecordingFile recording = new RecordingFile(trustedLocalRecording)) {
            while (recording.hasMoreEvents()) {
                if (scanned == MAX_EVENTS_TO_SCAN) {
                    truncated = true;
                    break;
                }
                RecordedEvent event = recording.readEvent();
                scanned++;
                String name = event.getEventType().getName();
                if (RuntimeTraceEvent.NAME.equals(name)) {
                    traces++;
                } else if (RuntimeWorkEvent.NAME.equals(name)) {
                    work++;
                    duration = Math.addExact(duration, event.getDuration().toNanos());
                }
            }
        }
        return new Summary(traces, work, duration, scanned, truncated);
    }
}
