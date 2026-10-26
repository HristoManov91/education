package bg.hristomanov.education.portmodulith.health.events;

/**
 * Published business fact in the health module's named interface.
 * No external message broker or durable delivery is implied.
 */
public record HealthAssessed(String vesselId, boolean accepted) {

    public HealthAssessed {
        if (vesselId == null || vesselId.isBlank()) {
            throw new IllegalArgumentException("vesselId must be present");
        }
    }
}
