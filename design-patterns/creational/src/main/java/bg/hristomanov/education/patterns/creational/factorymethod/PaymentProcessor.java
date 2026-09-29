package bg.hristomanov.education.patterns.creational.factorymethod;

public interface PaymentProcessor {

    PaymentReceipt pay(PaymentRequest request);
}
