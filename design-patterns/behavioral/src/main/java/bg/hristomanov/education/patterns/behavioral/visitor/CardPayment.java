package bg.hristomanov.education.patterns.behavioral.visitor;

import java.math.BigDecimal;

public record CardPayment(
        String reference,
        BigDecimal amount
) implements PaymentElement {

    @Override
    public <R> R accept(PaymentVisitor<R> visitor) {
        return visitor.visitCard(this);
    }
}
