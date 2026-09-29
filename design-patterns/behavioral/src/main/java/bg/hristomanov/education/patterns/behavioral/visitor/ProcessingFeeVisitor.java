package bg.hristomanov.education.patterns.behavioral.visitor;

import java.math.BigDecimal;

/**
 * Нова operation върху стабилната PaymentElement hierarchy,
 * без да добавяме fee logic във всеки domain element.
 */
public class ProcessingFeeVisitor implements PaymentVisitor<BigDecimal> {

    @Override
    public BigDecimal visitCard(CardPayment payment) {
        return payment.amount().multiply(new BigDecimal("0.015"));
    }

    @Override
    public BigDecimal visitBankTransfer(BankTransferPayment payment) {
        return new BigDecimal("0.50");
    }

    @Override
    public BigDecimal visitVoucher(VoucherPayment payment) {
        return BigDecimal.ZERO;
    }
}
