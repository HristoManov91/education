package bg.hristomanov.education.patterns.mastery.render;

import bg.hristomanov.education.patterns.mastery.model.ReportRequest;

public final class TextReportRenderer implements ReportRenderer {
    @Override public String format() { return "TEXT"; }

    @Override public String render(ReportRequest request) {
        return "Report: " + request.reference() + "\n"
                + "Owner: " + request.owner() + "\n"
                + String.join("\n", request.items());
    }
}
