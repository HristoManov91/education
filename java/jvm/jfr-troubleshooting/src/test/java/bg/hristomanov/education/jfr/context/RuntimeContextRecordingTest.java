package bg.hristomanov.education.jfr.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import jdk.jfr.Contextual;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real Java 25 JFR recording tests: no mocks and no attached AI services.
 * Both the original event metadata and on-disk event timing are inspected.
 */
class RuntimeContextRecordingTest {
    @TempDir
    Path tempDir;

    @Test
    void contextualFieldIsMarkedAndNestedWorkOccursWithinTraceOnSameThread() throws Exception {
        Path path = tempDir.resolve("context.jfr");

        try (Recording recording = recording()) {
            recording.start();
            RuntimeContextLab lab = new RuntimeContextLab();
            lab.run("lab-req-001", 120_000);
            lab.run("lab-req-002", 70_000);
            recording.stop();
            recording.dump(path);
        }

        List<RecordedEvent> events = RecordingFile.readAllEvents(path);
        List<RecordedEvent> traces = events.stream()
                .filter(e -> RuntimeTraceEvent.NAME.equals(e.getEventType().getName()))
                .sorted(Comparator.comparing(RecordedEvent::getStartTime))
                .toList();
        List<RecordedEvent> work = events.stream()
                .filter(e -> RuntimeWorkEvent.NAME.equals(e.getEventType().getName()))
                .sorted(Comparator.comparing(RecordedEvent::getStartTime))
                .toList();

        assertThat(traces).hasSize(2);
        assertThat(work).hasSize(2);

        // Verify the actual JFR file contains the Java 25 @Contextual metadata.
        var contextualField = traces.getFirst().getEventType().getField("traceId");
        assertThat(contextualField).isNotNull();
        assertThat(contextualField.getAnnotation(Contextual.class)).isNotNull();

        assertThat(traces).extracting(e -> e.getString("traceId"))
                .containsExactly("lab-req-001", "lab-req-002");
        assertThat(traces).allSatisfy(e -> assertThat(e.getString("operation"))
                .isEqualTo("synthetic-cpu"));
        assertThat(work).extracting(e -> e.getString("phase"))
                .containsExactly("checksum", "checksum");

        // The lower-level phase deliberately has no duplicated traceId field.
        assertThat(work.getFirst().getEventType().getField("traceId")).isNull();
        for (int i = 0; i < traces.size(); i++) {
            RecordedEvent trace = traces.get(i);
            RecordedEvent phase = work.get(i);
            assertThat(phase.getThread("eventThread").getJavaThreadId())
                    .isEqualTo(trace.getThread("eventThread").getJavaThreadId());
            assertThat(phase.getStartTime()).isAfterOrEqualTo(trace.getStartTime());
            assertThat(phase.getEndTime()).isBeforeOrEqualTo(trace.getEndTime());
            assertThat(phase.getInt("iterations")).isPositive();
        }
    }

    @Test
    void allowlistedEvidenceReportNeverIncludesTraceIdsOrOtherEventPayload() throws Exception {
        Path path = tempDir.resolve("safe-local-fixture.jfr");

        try (Recording recording = recording()) {
            recording.enable(SecretDistractorEvent.class).withThreshold(Duration.ZERO);
            recording.start();

            new RuntimeContextLab().run("lab-private-id", 20_000);
            SecretDistractorEvent distractor = new SecretDistractorEvent();
            distractor.text = "DUMMY_NOT_A_REAL_SECRET";
            distractor.commit();

            recording.stop();
            recording.dump(path);
        }

        JfrEvidenceSummary.Summary summary = JfrEvidenceSummary.analyze(path);
        String text = summary.toReviewText();

        assertThat(summary.traceEvents()).isEqualTo(1);
        assertThat(summary.workEvents()).isEqualTo(1);
        assertThat(summary.workDurationNanos()).isGreaterThanOrEqualTo(0L);
        assertThat(summary.truncated()).isFalse();
        assertThat(text).contains("trace_events=1", "work_events=1",
                "Interpretation: counts and duration only");
        assertThat(text).doesNotContain("lab-private-id", "DUMMY_NOT_A_REAL_SECRET",
                path.toString(), "traceId", "password");
    }

    @Test
    void rejectsRealisticOrSecretLikeIdentifiersAndUnboundedWork() {
        RuntimeContextLab lab = new RuntimeContextLab();

        assertThrows(NullPointerException.class, () -> lab.run(null, 10));
        assertThrows(IllegalArgumentException.class,
                () -> lab.run("Authorization: Bearer abc", 10));
        assertThrows(IllegalArgumentException.class,
                () -> lab.run("customer@company.com", 10));
        assertThrows(IllegalArgumentException.class, () -> lab.run("lab-", 10));
        assertThrows(IllegalArgumentException.class, () -> lab.run("lab-valid", 0));
        assertThrows(IllegalArgumentException.class, () -> lab.run("lab-valid", 250_001));
    }

    @Test
    void sameInputProducesSameChecksumWithoutNeedingARecording() {
        RuntimeContextLab lab = new RuntimeContextLab();
        assertThat(lab.run("lab-one", 7_000)).isEqualTo(lab.run("lab-two", 7_000));
    }

    private static Recording recording() {
        Recording recording = new Recording();
        recording.enable(RuntimeTraceEvent.class).withThreshold(Duration.ZERO);
        recording.enable(RuntimeWorkEvent.class).withThreshold(Duration.ZERO);
        return recording;
    }

    @Name("bg.hristomanov.education.jfr.SecretDistractor")
    static final class SecretDistractorEvent extends Event {
        String text;
    }
}
