package bg.hristomanov.education.loom.bankapi.service;

import bg.hristomanov.education.loom.bankapi.client.BankClients.CustomerClient;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.CustomerInfo;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.LoanApplicationRequest;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.Offer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Business orchestration layer за loan application use case-а.
 *
 * <p>Този class показва една важна граница: concurrency трябва да следва dependency graph-а,
 * а не да се добавя механично навсякъде.</p>
 *
 * <p>Първо зареждаме Customer последователно, защото следващите calls използват неговото id.
 * Едва след като prerequisite-ът е наличен, StructuredCustomerInfoLoader fan-out-ва независимите
 * accounts/loans/credit-score операции.</p>
 */
@Service
public class LoanApplicationService {

    private final CustomerClient customerClient;
    private final StructuredCustomerInfoLoader customerInfoLoader;

    public LoanApplicationService(
            CustomerClient customerClient,
            StructuredCustomerInfoLoader customerInfoLoader) {
        this.customerClient = customerClient;
        this.customerInfoLoader = customerInfoLoader;
    }

    public Offer apply(LoanApplicationRequest request) {
        /*
         * Не fork-ваме този call заедно с останалите само защото можем.
         * Customer е prerequisite за downstream fan-out-а и така dependency-то остава explicit.
         */
        var customer = customerClient.getCustomer(request.customerId());

        /*
         * Оттук надолу имаме няколко независими I/O операции. Точно там concurrency носи
         * реална latency полза и StructuredCustomerInfoLoader поема ownership-а им.
         */
        var customerInfo = customerInfoLoader.load(customer);

        /*
         * След fan-in-а отново сме в нормален sequential business flow.
         * Самото изчисляване на офертата не става „по-добро“, ако го пуснем в още един thread.
         */
        return calculateOffer(request, customerInfo);
    }

    private Offer calculateOffer(LoanApplicationRequest request, CustomerInfo info) {
        /*
         * Banking логиката тук е умишлено опростена. Лабораторията е за concurrency model-а,
         * не за реален credit-risk engine.
         */
        int score = info.creditScore().score();

        if (score < 650) {
            return new Offer(request.customerId(), false, request.amount(), null,
                    "Credit score is below the demo approval threshold");
        }

        BigDecimal interestRate;
        if (score >= 750) {
            interestRate = new BigDecimal("3.50");
        } else if (score >= 700) {
            interestRate = new BigDecimal("4.20");
        } else {
            interestRate = new BigDecimal("5.10");
        }

        return new Offer(request.customerId(), true, request.amount(), interestRate,
                "Demo offer calculated from the first successful credit score");
    }
}
