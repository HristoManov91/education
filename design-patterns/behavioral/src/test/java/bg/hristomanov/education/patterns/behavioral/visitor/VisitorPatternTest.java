package bg.hristomanov.education.patterns.behavioral.visitor;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VisitorPatternTest {

    @Test
    void newOperationsCanBeAppliedAcrossStablePaymentHierarchy() {
        List<PaymentElement> payments = List.of(
                new CardPayment("C-1", new BigDecimal("100.00")),
                new BankTransferPayment("B-1", new BigDecimal("200.00")),
                new VoucherPayment("V-1", new BigDecimal("50.00"))
        );

        PaymentVisitor<BigDecimal> feeVisitor = new ProcessingFeeVisitor();
        PaymentVisitor<String> labelVisitor = new ComplianceLabelVisitor();

        List<BigDecimal> fees = payments.stream()
                .map(payment -> payment.accept(feeVisitor))
                .toList();

        List<String> labels = payments.stream()
                .map(payment -> payment.accept(labelVisitor))
                .toList();

        assertThat(fees.get(0)).isEqualByComparingTo("1.50000");
        assertThat(fees.get(1)).isEqualByComparingTo("0.50");
        assertThat(fees.get(2)).isEqualByComparingTo("0");

        assertThat(labels).containsExactly(
                "CARD:C-1",
                "BANK_TRANSFER:B-1",
                "VOUCHER:V-1"
        );
    }
}
