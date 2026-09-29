package bg.hristomanov.education.patterns.creational.factorymethod;

public class BankTransferPaymentProcessor implements PaymentProcessor {

    @Override
    public PaymentReceipt pay(PaymentRequest request) {
        return new PaymentReceipt("bank-transfer", request.reference());
    }
}
