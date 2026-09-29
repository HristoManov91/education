package bg.hristomanov.education.patterns.creational.prototype;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PrototypePatternTest {

    @Test
    void registryReturnsIndependentCopiesWithoutExposingConcreteConstruction() {
        ReportTemplateRegistry registry = new ReportTemplateRegistry();
        registry.register(
                new CsvReportTemplate(
                        "monthly-payments",
                        Set.of("paymentId", "amount", "currency"),
                        Map.of("status", "PAID")
                )
        );

        ReportTemplate first = registry.createFrom("monthly-payments");
        ReportTemplate second = registry.createFrom("monthly-payments");

        first.putFilter("country", "BG");

        assertThat(first).isNotSameAs(second);
        assertThat(first.filters()).containsEntry("country", "BG");
        assertThat(second.filters()).doesNotContainKey("country");
        assertThat(second.filters()).containsEntry("status", "PAID");
    }
}
