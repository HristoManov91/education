package bg.hristomanov.education.loom.bankapi.service;

import bg.hristomanov.education.loom.bankapi.client.BankClients.CustomerClient;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.Customer;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.CustomerInfo;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.LoanApplicationRequest;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.Offer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Business orchestration layer (слоят, който подрежда стъпките на бизнес операцията)
 * за loan application use case-а.
 *
 * <p>Този class показва една важна граница: concurrency (едновременно изпълнение)
 * трябва да следва dependency graph-а (коя операция от коя зависи),
 * а не да се добавя механично навсякъде.</p>
 *
 * <p>Първо зареждаме Customer последователно, защото следващите calls използват неговото id.
 * Едва след като prerequisite-ът (задължителната предварителна стъпка) е наличен,
 * StructuredCustomerInfoLoader прави fan-out (разклоняване към няколко независими операции)
 * към accounts/loans/credit-score.</p>
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
         * Customer е prerequisite (предварително нужен резултат) за downstream fan-out-а
         * (разклоняването към следващите извиквани операции), затова dependency-то остава explicit
         * (видимо директно от структурата на кода).
         *
         * Умишлено пишем explicit type вместо var: в учебния проект искаме веднага да се вижда,
         * че customerClient.getCustomer(...) връща Customer.
         */
        Customer customer = customerClient.getCustomer(request.customerId());

        /*
         * Оттук надолу имаме няколко независими I/O операции. Точно там concurrency
         * (едновременно изпълнение) носи реална latency полза (по-ниско общо време на заявката),
         * а StructuredCustomerInfoLoader поема ownership-а им
         * (отговорността да стартира, изчака и приключи тези задачи коректно).
         */
        CustomerInfo customerInfo = customerInfoLoader.load(customer);

        /*
         * След fan-in-а (събирането на паралелните резултати обратно в един поток)
         * отново сме в нормален sequential business flow (последователен бизнес поток).
         * Самото изчисляване на офертата не става „по-добро“, ако го пуснем в още един thread.
         */
        return calculateOffer(request, customerInfo);
    }

    private Offer calculateOffer(LoanApplicationRequest request, CustomerInfo info) {
        /*
         * Banking логиката тук е умишлено опростена. Лабораторията е за concurrency model-а
         * (модела за едновременно изпълнение), не за реален credit-risk engine.
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
