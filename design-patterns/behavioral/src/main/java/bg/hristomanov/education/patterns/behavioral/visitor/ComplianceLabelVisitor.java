package bg.hristomanov.education.patterns.behavioral.visitor;

public class ComplianceLabelVisitor implements PaymentVisitor<String> {

    @Override
    public String visitCard(CardPayment payment) {
        return "CARD:" + payment.reference();
    }

    @Override
    public String visitBankTransfer(BankTransferPayment payment) {
        return "BANK_TRANSFER:" + payment.reference();
    }

    @Override
    public String visitVoucher(VoucherPayment payment) {
        return "VOUCHER:" + payment.reference();
    }
}
