package bg.hristomanov.education.loom.bankapi.service;

import bg.hristomanov.education.loom.bankapi.client.BankClients.CustomerClient;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.CustomerInfo;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.LoanApplicationRequest;
import bg.hristomanov.education.loom.bankapi.domain.BankModels.Offer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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
        var customer = customerClient.getCustomer(request.customerId());
        var customerInfo = customerInfoLoader.load(customer);
        return calculateOffer(request, customerInfo);
    }

    private Offer calculateOffer(LoanApplicationRequest request, CustomerInfo info) {
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
