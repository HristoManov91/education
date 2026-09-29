package bg.hristomanov.education.patterns.creational.factorymethod;

public class CardPaymentProcessor implements PaymentProcessor {

    @Override
    public PaymentReceipt pay(PaymentRequest request) {
        return new PaymentReceipt("card", request.reference());
    }
}
