package bg.hristomanov.education.patterns.behavioral.visitor;

import java.math.BigDecimal;

public record VoucherPayment(
        String reference,
        BigDecimal amount
) implements PaymentElement {

    @Override
    public <R> R accept(PaymentVisitor<R> visitor) {
        return visitor.visitVoucher(this);
    }
}
