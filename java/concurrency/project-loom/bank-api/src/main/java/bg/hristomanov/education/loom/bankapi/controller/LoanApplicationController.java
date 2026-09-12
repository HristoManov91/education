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

/**
 * HTTP boundary (границата, през която HTTP заявката влиза в приложението) на demo приложението.
 *
 * <p>Точно тук bind-ваме (свързваме с текущия execution scope) request metadata
 * (данните за конкретната заявка, тук основно requestId) чрез ScopedValue, защото controller-ът е
 * естествената граница на една HTTP операция. Всичко, което се извика надолу от
 * {@link #apply(LoanApplicationRequest)}, принадлежи на същия request.</p>
 *
 * <p>Ако bind-нем context-а по-късно, например чак в HTTP client, sibling operations
 * (паралелни операции на едно и също ниво с общ parent) няма да споделят една и съща
 * request identity (идентичност на конкретната заявка, например нейния requestId).
 * Ако пък го пазим в mutable singleton field (споделено изменяемо поле в един singleton bean),
 * concurrent requests (едновременно обработвани заявки) могат да си презаписват стойностите.</p>
 */
@RestController
@RequestMapping("/api")
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;

    public LoanApplicationController(LoanApplicationService loanApplicationService) {
        this.loanApplicationService = loanApplicationService;
    }

    @PostMapping("/loan-applications")
    public Offer apply(@RequestBody LoanApplicationRequest request) {
        /*
         * В реално приложение requestId често идва от incoming header (входящ HTTP header),
         * gateway (входен посредник/маршрутизатор) или tracing system (система за проследяване).
         * Тук генерираме UUID локално, за да държим лабораторията самостоятелна.
         *
         * Explicit type-ът RequestMetadata е умишлен за учебния проект: така резултатът от
         * конструкцията се вижда директно и не разчитаме на var inference.
         */
        RequestMetadata metadata = new RequestMetadata(UUID.randomUUID());

        /*
         * RequestContext.call(...) отваря dynamic ScopedValue binding
         * (временно свързване на стойност с текущия execution scope).
         *
         * Докато lambda-та се изпълнява:
         * - service/loader/client кодът може да прочете RequestContext.current();
         * - StructuredTaskScope child threads (дъщерни нишки на текущата structured операция)
         *   наследяват binding-а;
         * - не е нужно requestId да се прокарва през всеки method parameter.
         *
         * След края на call(...) binding-ът автоматично приключва. Няма ръчно remove(),
         * както при типичен ThreadLocal lifecycle (жизнен цикъл).
         */
        return RequestContext.call(metadata, () -> loanApplicationService.apply(request));
    }

    @GetMapping("/thread-info")
    public ThreadInfo threadInfo() {
        /*
         * Учебен диагностичен endpoint: искаме да докажем, че request code-ът действително
         * може да се изпълнява върху virtual thread, а не само да разчитаме на property-то.
         *
         * В production обикновено не бихме expose-вали подобен endpoint.
         */
        Thread thread = Thread.currentThread();
        return new ThreadInfo(thread.toString(), thread.isVirtual());
    }
}
