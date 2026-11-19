package bg.hristomanov.education.patterns.mastery.render;

import bg.hristomanov.education.patterns.mastery.model.ReportRequest;

public interface ReportRenderer {
    String format();
    String render(ReportRequest request);
}
