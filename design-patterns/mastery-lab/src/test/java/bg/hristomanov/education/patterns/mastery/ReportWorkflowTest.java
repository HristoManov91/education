package bg.hristomanov.education.patterns.mastery;

import bg.hristomanov.education.patterns.mastery.archive.*;
import bg.hristomanov.education.patterns.mastery.model.*;
import bg.hristomanov.education.patterns.mastery.render.*;
import bg.hristomanov.education.patterns.mastery.validate.ReportValidation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class ReportWorkflowTest {
    private static ReportValidation validation() {
        return new ReportValidation(List.of(ReportValidation.requiredMetadata(),
                ReportValidation.nonEmptyItems()));
    }
    private static RendererRegistry renderers() {
        return new RendererRegistry(List.of(new TextReportRenderer(), new CsvReportRenderer()));
    }

    @Test void facadeSelectsStrategyAndAdaptsIncompatibleArchiveApi() {
        LegacyArchiveClient legacy = new LegacyArchiveClient();
        AuditTrail audit = new AuditTrail();
        ReportWorkflow workflow = new ReportWorkflow(validation(), renderers(),
                new AuditedArchiveGateway(new LegacyArchiveAdapter(legacy), audit));

        ReportReceipt receipt = workflow.submit(new ReportRequest("R-1", "Ada",
                List.of("A", "B"), "csv"));

        assertThat(receipt.format()).isEqualTo("CSV");
        assertThat(receipt.archiveId()).isEqualTo("LEGACY:R-1");
        assertThat(legacy.readLegacyText("R-1")).contains("\"R-1\",\"A\"");
        assertThat(audit.entries()).containsExactly("ARCHIVE_OK:R-1");
    }

    @Test void validationStopsBeforeRenderingAndArchiving() {
        AtomicInteger archiveCalls = new AtomicInteger();
        ReportWorkflow workflow = new ReportWorkflow(validation(), renderers(),
                (reference, content) -> {
                    archiveCalls.incrementAndGet();
                    return "unexpected";
                });
        assertThatThrownBy(() -> workflow.submit(
                new ReportRequest("R-2", " ", List.of("X"), "TEXT")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(archiveCalls.get()).isZero();
    }

    @Test void ruleChainShortCircuitsAfterFirstFailure() {
        AtomicInteger secondRule = new AtomicInteger();
        ReportValidation validation = new ReportValidation(List.of(
                request -> { throw new IllegalArgumentException("first rule"); },
                request -> secondRule.incrementAndGet()));
        assertThatThrownBy(() -> validation.verify(
                new ReportRequest("R-3", "Ada", List.of("X"), "TEXT")))
                .hasMessage("first rule");
        assertThat(secondRule.get()).isZero();
    }

    @Test void unsupportedFormatCannotCreateArchivedDocument() {
        LegacyArchiveClient legacy = new LegacyArchiveClient();
        ReportWorkflow workflow = new ReportWorkflow(validation(), renderers(),
                new LegacyArchiveAdapter(legacy));
        assertThatThrownBy(() -> workflow.submit(new ReportRequest("R-4", "Ada",
                List.of("X"), "XML"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(legacy.readLegacyText("R-4")).isNull();
    }

    @Test void failedArchivePropagatesAndIsRecordedAsFailure() {
        AuditTrail audit = new AuditTrail();
        ReportWorkflow workflow = new ReportWorkflow(validation(), renderers(),
                new AuditedArchiveGateway((key, content) -> {
                    throw new IllegalStateException("archive unavailable");
                }, audit));
        assertThatThrownBy(() -> workflow.submit(new ReportRequest("R-5", "Ada",
                List.of("X"), "TEXT")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("archive unavailable");
        assertThat(audit.entries()).containsExactly("ARCHIVE_FAILED:R-5");
    }

    @Test void duplicateStrategyRegistrationIsRejected() {
        assertThatThrownBy(() -> new RendererRegistry(List.of(
                new TextReportRenderer(), new TextReportRenderer())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate");
    }

    @Test void csvEscapesQuotesAndNeutralizesSpreadsheetFormulas() {
        String csv = new CsvReportRenderer().render(
                new ReportRequest("R-6", "Ada", List.of("a,\"b\"", "=1+1"), "CSV"));
        assertThat(csv).contains("\"a,\"\"b\"\"\"");
        assertThat(csv).contains("\"'=1+1\"");
    }
}
