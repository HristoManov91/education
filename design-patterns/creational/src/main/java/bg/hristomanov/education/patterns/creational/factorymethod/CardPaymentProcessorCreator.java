package bg.hristomanov.education.patterns.creational.factorymethod;

public class CardPaymentProcessorCreator extends PaymentProcessorCreator {

    @Override
    protected PaymentProcessor createProcessor() {
        return new CardPaymentProcessor();
    }
}
