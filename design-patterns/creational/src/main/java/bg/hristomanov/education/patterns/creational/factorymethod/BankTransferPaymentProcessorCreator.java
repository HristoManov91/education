package bg.hristomanov.education.patterns.creational.factorymethod;

public class BankTransferPaymentProcessorCreator extends PaymentProcessorCreator {

    @Override
    protected PaymentProcessor createProcessor() {
        return new BankTransferPaymentProcessor();
    }
}
