package bg.hristomanov.education.aitesting.service;

import bg.hristomanov.education.aitesting.domain.RegisteredCustomer;
import bg.hristomanov.education.aitesting.domain.RegistrationCommand;
import bg.hristomanov.education.aitesting.domain.RegistrationPolicy;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Intentional in-memory fixture; each caller reads only by returned ID.
 * It is NOT a replacement for a durable repository or database integration test.
 */
@Service
public class CustomerService {

    private final RegistrationPolicy policy;
    private final Clock clock;
    private final ConcurrentMap<UUID, RegisteredCustomer> customers = new ConcurrentHashMap<>();

    public CustomerService(RegistrationPolicy policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
    }

    public RegisteredCustomer register(RegistrationCommand command) {
        var decision = policy.evaluate(command.birthDate(), command.acceptedTerms(), clock);
        if (decision != RegistrationPolicy.Decision.ELIGIBLE) {
            throw new RegistrationRejectedException(decision.name());
        }
        var customer = new RegisteredCustomer(UUID.randomUUID(), command.email(), Instant.now(clock));
        customers.put(customer.id(), customer);
        return customer;
    }

    public Optional<RegisteredCustomer> find(UUID id) {
        return Optional.ofNullable(customers.get(id));
    }
}
