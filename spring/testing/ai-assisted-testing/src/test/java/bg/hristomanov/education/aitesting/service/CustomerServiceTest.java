package bg.hristomanov.education.aitesting.service;

import bg.hristomanov.education.aitesting.domain.RegistrationCommand;
import bg.hristomanov.education.aitesting.domain.RegistrationPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;

class CustomerServiceTest {

    @Test
    void successfulRegistrationReturnsStoredCustomerAndClockControlledTimestamp() {
        Instant instant = Instant.parse("2026-10-26T08:00:00Z");
        var service = new CustomerService(new RegistrationPolicy(), Clock.fixed(instant, ZoneOffset.UTC));

        var customer = service.register(new RegistrationCommand(
                "alice@example.com", LocalDate.of(1990, 1, 1), true));

        assertThat(customer.id()).isNotNull();
        assertThat(customer.registeredAt()).isEqualTo(instant);
        assertThat(customer.email()).isEqualTo("alice@example.com");
        assertThat(service.find(customer.id())).contains(customer);
    }

    @Test
    void serviceDoesNotStoreUnderageRequest() {
        var service = new CustomerService(new RegistrationPolicy(),
                Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.register(
                new RegistrationCommand("minor@example.com", LocalDate.of(2012, 1, 1), true)))
                .isInstanceOf(RegistrationRejectedException.class)
                .hasMessage("TOO_YOUNG");
    }
}
