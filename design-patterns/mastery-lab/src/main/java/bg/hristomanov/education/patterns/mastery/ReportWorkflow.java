package bg.hristomanov.education.patterns.mastery;

import bg.hristomanov.education.patterns.mastery.archive.ArchiveGateway;
import bg.hristomanov.education.patterns.mastery.model.ReportReceipt;
import bg.hristomanov.education.patterns.mastery.model.ReportRequest;
import bg.hristomanov.education.patterns.mastery.render.RendererRegistry;
import bg.hristomanov.education.patterns.mastery.render.ReportRenderer;
import bg.hristomanov.education.patterns.mastery.validate.ReportValidation;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Use-case Facade: validation -> chosen Strategy -> outbound Adapter/Decorator. */
public final class ReportWorkflow {
    private final ReportValidation validation;
    private final RendererRegistry renderers;
    private final ArchiveGateway archive;

    public ReportWorkflow(ReportValidation validation, RendererRegistry renderers, ArchiveGateway archive) {
        this.validation = Objects.requireNonNull(validation, "validation");
        this.renderers = Objects.requireNonNull(renderers, "renderers");
        this.archive = Objects.requireNonNull(archive, "archive");
    }

    public ReportReceipt submit(ReportRequest request) {
        validation.verify(request);
        ReportRenderer renderer = renderers.forFormat(request.format());
        String content = renderer.render(request);
        String archiveId = archive.archive(request.reference(), content);
        return new ReportReceipt(request.reference(), archiveId, renderer.format(),
                content.getBytes(StandardCharsets.UTF_8).length);
    }
}
