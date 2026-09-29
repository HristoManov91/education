package bg.hristomanov.education.patterns.creational.abstractfactory;

/**
 * Abstract Factory създава семейство от свързани objects.
 *
 * <p>Целта не е просто да скрием {@code new}, а да гарантираме, че компонентите
 * за един регион/tenant/provider се създават като съвместима family.</p>
 */
public interface CommerceFactory {

    TaxCalculator createTaxCalculator();

    PaymentGateway createPaymentGateway();
}
