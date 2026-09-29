package bg.hristomanov.education.hexagonal.port.in;

/**
 * Driving/input port.
 *
 * <p>REST, batch job, CLI или automated test могат да drive-нат application-а
 * през този contract. Port-ът е именуван по purpose/use case, не по HTTP.</p>
 */
public interface PlaceOrderUseCase {

    OrderView place(PlaceOrderCommand command);
}
