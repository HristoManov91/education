package bg.hristomanov.education.jfr.jfr;

import bg.hristomanov.education.jfr.domain.LabResults.OrderResult;
import bg.hristomanov.education.jfr.service.CpuHotspotService;
import bg.hristomanov.education.jfr.service.OrderProcessingService;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Доказва custom JFR event-а без JMC и без ръчна проверка.
 *
 * <p>Тестът стартира истински Flight Recorder recording в текущата test JVM,
 * изпълнява business операцията, dump-ва .jfr файл и после го прочита обратно
 * чрез официалния JFR consumer API.</p>
 */
class OrderProcessingEventRecordingTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldRecordApplicationSpecificOrderEvent() throws Exception {
        CpuHotspotService cpuHotspotService = new CpuHotspotService();
        OrderProcessingService service = new OrderProcessingService(cpuHotspotService);
        Path recordingFile = tempDirectory.resolve("order-processing.jfr");

        OrderResult result;

        try (Recording recording = new Recording()) {
            recording.enable(OrderProcessingEvent.class)
                    .withThreshold(Duration.ZERO)
                    .withStackTrace();

            recording.start();
            result = service.process(2);
            recording.stop();
            recording.dump(recordingFile);
        }

        List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);

        RecordedEvent orderEvent = events.stream()
                .filter(event -> OrderProcessingEvent.NAME.equals(event.getEventType().getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("OrderProcessing JFR event was not recorded"));

        assertThat(orderEvent.getString("orderId")).isEqualTo(result.orderId());
        assertThat(orderEvent.getInt("itemCount")).isEqualTo(2);
        assertThat(orderEvent.getString("result")).isEqualTo("SUCCESS");
        assertThat(orderEvent.getDuration().toNanos()).isGreaterThan(0L);
        assertThat(orderEvent.getStackTrace()).isNotNull();
    }
}
