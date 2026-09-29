package bg.hristomanov.education.patterns.creational.abstractfactory;

import java.math.BigDecimal;

public interface PaymentGateway {

    String charge(BigDecimal amount);
}
