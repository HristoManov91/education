package bg.hristomanov.education.patterns.behavioral.visitor;

public interface PaymentVisitor<R> {

    R visitCard(CardPayment payment);

    R visitBankTransfer(BankTransferPayment payment);

    R visitVoucher(VoucherPayment payment);
}
