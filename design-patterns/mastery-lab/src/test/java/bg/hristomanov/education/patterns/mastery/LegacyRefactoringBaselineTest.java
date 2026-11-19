package bg.hristomanov.education.patterns.mastery;

import bg.hristomanov.education.patterns.mastery.legacy.LegacyReportWorkflow;
import bg.hristomanov.education.patterns.mastery.model.*;
import bg.hristomanov.education.patterns.mastery.render.*;
import bg.hristomanov.education.patterns.mastery.validate.ReportValidation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class LegacyRefactoringBaselineTest {
    @Test void legacyAndComposedWorkflowAgreeOnHappyPath() {
        ReportRequest request = new ReportRequest("K-1", "Ada", List.of("one", "two"), "CSV");
        AtomicReference<String> oldPayload = new AtomicReference<>();
        AtomicReference<String> newPayload = new AtomicReference<>();
        ReportReceipt oldResult = new LegacyReportWorkflow((key, body) -> {
            oldPayload.set(body); return "arch:" + key;
        }).submit(request);

        ReportWorkflow newWorkflow = new ReportWorkflow(
                new ReportValidation(List.of(ReportValidation.requiredMetadata(),
                        ReportValidation.nonEmptyItems())),
                new RendererRegistry(List.of(new CsvReportRenderer(), new TextReportRenderer())),
                (key, body) -> { newPayload.set(body); return "arch:" + key; });
        ReportReceipt newResult = newWorkflow.submit(request);

        assertThat(newResult).isEqualTo(oldResult);
        assertThat(newPayload.get()).isEqualTo(oldPayload.get());
    }

    @Test void naiveCsvBaselineExposesFormulaTokenWhereImprovedRendererNeutralizesIt() {
        ReportRequest request = new ReportRequest("K-2", "Ada", List.of("=1+1"), "CSV");
        AtomicReference<String> legacyPayload = new AtomicReference<>();
        new LegacyReportWorkflow((key, body) -> {
            legacyPayload.set(body); return "arch:" + key;
        }).submit(request);
        String improved = new CsvReportRenderer().render(request);

        assertThat(legacyPayload.get()).contains("\"=1+1\"");
        assertThat(improved).contains("\"'=1+1\"");
    }
}
