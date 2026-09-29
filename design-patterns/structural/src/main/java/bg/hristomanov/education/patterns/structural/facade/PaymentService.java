package bg.hristomanov.education.patterns.structural.facade;

import java.math.BigDecimal;

public class PaymentService {

    public String charge(String customerId, BigDecimal amount) {
        return "PAY-" + customerId + "-" + amount;
    }
}
