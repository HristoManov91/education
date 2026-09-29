package bg.hristomanov.education.patterns.behavioral.chain;

import java.util.Optional;
import java.util.Set;

public class SupportedCountryValidation extends OrderValidationHandler {

    private final Set<String> supportedCountries;

    public SupportedCountryValidation(Set<String> supportedCountries) {
        this.supportedCountries = Set.copyOf(supportedCountries);
    }

    @Override
    protected Optional<String> validateCurrent(OrderDraft order) {
        if (!supportedCountries.contains(order.countryCode())) {
            return Optional.of("unsupported country: " + order.countryCode());
        }
        return Optional.empty();
    }
}
