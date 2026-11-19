package bg.hristomanov.education.patterns.mastery;

import bg.hristomanov.education.patterns.mastery.model.ReportReceipt;
import bg.hristomanov.education.patterns.mastery.model.ReportRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportWorkflow workflow;

    public ReportController(ReportWorkflow workflow) {
        this.workflow = workflow;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportReceipt create(@RequestBody ReportRequest request) {
        return workflow.submit(request);
    }
}
