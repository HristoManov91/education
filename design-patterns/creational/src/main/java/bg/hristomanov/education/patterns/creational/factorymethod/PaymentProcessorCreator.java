package bg.hristomanov.education.patterns.creational.factorymethod;

/**
 * Factory Method: base workflow-ът работи с abstraction, а subclass-ът решава
 * кой concrete product да създаде чрез factory method-а {@link #createProcessor()}.
 */
public abstract class PaymentProcessorCreator {

    public PaymentReceipt process(PaymentRequest request) {
        PaymentProcessor processor = createProcessor();
        return processor.pay(request);
    }

    protected abstract PaymentProcessor createProcessor();
}
