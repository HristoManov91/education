package bg.hristomanov.education.patterns.mastery;

import bg.hristomanov.education.patterns.mastery.archive.*;
import bg.hristomanov.education.patterns.mastery.render.*;
import bg.hristomanov.education.patterns.mastery.validate.ReportValidation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

/** Explicit Spring composition root; the domain design does not require Spring annotations. */
@Configuration(proxyBeanMethods = false)
public class ReportWiring {
    @Bean RendererRegistry rendererRegistry() {
        return new RendererRegistry(List.of(new TextReportRenderer(), new CsvReportRenderer()));
    }
    @Bean ReportValidation reportValidation() {
        return new ReportValidation(List.of(ReportValidation.requiredMetadata(),
                ReportValidation.nonEmptyItems()));
    }
    @Bean LegacyArchiveClient legacyArchiveClient() { return new LegacyArchiveClient(); }
    @Bean AuditTrail auditTrail() { return new AuditTrail(); }
    @Bean ArchiveGateway archiveGateway(LegacyArchiveClient legacy, AuditTrail trail) {
        return new AuditedArchiveGateway(new LegacyArchiveAdapter(legacy), trail);
    }
    @Bean ReportWorkflow reportWorkflow(ReportValidation validation,
            RendererRegistry renderers, ArchiveGateway archive) {
        return new ReportWorkflow(validation, renderers, archive);
    }
}
