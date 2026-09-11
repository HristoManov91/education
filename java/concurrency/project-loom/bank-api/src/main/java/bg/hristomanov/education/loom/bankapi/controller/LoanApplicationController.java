package bg.hristomanov.education.loom.bankapi.controller;

import bg.hristomanov.education.loom.bankapi.context.RequestContext;
import bg.hristomanov.education.loom.bankapi.context.RequestContext.RequestMetadata;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.LoanApplicationRequest;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.Offer;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.ThreadInfo;
import bg.hristomanov.education.loom.bankapi.service.LoanApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;

    public LoanApplicationController(LoanApplicationService loanApplicationService) {
        this.loanApplicationService = loanApplicationService;
    }

    @PostMapping("/loan-applications")
    public Offer apply(@RequestBody LoanApplicationRequest request) {
        var metadata = new RequestMetadata(UUID.randomUUID());

        // Binding-ът важи само за динамичния scope на тази операция и се наследява
        // от child threads, създадени от StructuredTaskScope.
        return RequestContext.call(metadata, () -> loanApplicationService.apply(request));
    }

    @GetMapping("/thread-info")
    public ThreadInfo threadInfo() {
        var thread = Thread.currentThread();
        return new ThreadInfo(thread.toString(), thread.isVirtual());
    }
}
