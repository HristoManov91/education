package bg.hristomanov.education.aitesting.domain;

import java.time.Instant;
import java.util.UUID;

public record RegisteredCustomer(UUID id, String email, Instant registeredAt) {
}
