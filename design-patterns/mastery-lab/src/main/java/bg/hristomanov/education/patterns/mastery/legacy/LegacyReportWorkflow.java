package bg.hristomanov.education.patterns.mastery.legacy;

import bg.hristomanov.education.patterns.mastery.archive.ArchiveGateway;
import bg.hristomanov.education.patterns.mastery.model.ReportReceipt;
import bg.hristomanov.education.patterns.mastery.model.ReportRequest;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Deliberately naive baseline for the refactoring kata, NOT the production HTTP path.
 *
 * One method owns validation, format selection, rendering and archiving.
 * CSV handles basic quoting but misses spreadsheet-formula neutralization.
 */
public final class LegacyReportWorkflow {
    private final ArchiveGateway archive;

    public LegacyReportWorkflow(ArchiveGateway archive) {
        this.archive = Objects.requireNonNull(archive, "archive");
    }

    public ReportReceipt submit(ReportRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.owner() == null || request.owner().isBlank()
                || request.reference() == null || request.reference().isBlank()) {
            throw new IllegalArgumentException("reference and owner are required");
        }
        if (request.items() == null || request.items().isEmpty()
                || request.items().stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("at least one nonblank item is required");
        }
        String rendered;
        String format;
        if ("TEXT".equalsIgnoreCase(request.format())) {
            format = "TEXT";
            rendered = "Report: " + request.reference() + "\n"
                    + "Owner: " + request.owner() + "\n"
                    + String.join("\n", request.items());
        } else if ("CSV".equalsIgnoreCase(request.format())) {
            format = "CSV";
            StringBuilder csv = new StringBuilder("reference,item\r\n");
            for (String item : request.items()) {
                csv.append("\"").append(request.reference().replace("\"", "\"\"")).append("\"")
                        .append(",\"").append(item.replace("\"", "\"\"")).append("\"\r\n");
            }
            rendered = csv.toString();
        } else {
            throw new IllegalArgumentException("unsupported report format");
        }
        String id = archive.archive(request.reference(), rendered);
        return new ReportReceipt(request.reference(), id, format,
                rendered.getBytes(StandardCharsets.UTF_8).length);
    }
}
