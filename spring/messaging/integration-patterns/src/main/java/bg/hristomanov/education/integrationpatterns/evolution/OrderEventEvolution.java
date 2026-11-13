package bg.hristomanov.education.integrationpatterns.evolution;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure Java 25 example: expand-readers-before-writers and recipient capability negotiation.
 * The wire format is deliberately small and explicit so its behavior can be proven
 * independently of Kafka, Spring MVC, HTTP headers, and dynamic discovery.
 *
 * Only the JUnit adapter serializes these maps to REAL JSON via the existing
 * test-scoped Jackson dependency. No new Maven runtime dependency is required.
 */
public final class OrderEventEvolution {

    private OrderEventEvolution() {}

    public enum Format { V1, V2 }
    public enum Currency { EUR, USD }

    public record Order(String id, long amountCents, Currency currency) {
        public Order {
            if (id == null || id.isBlank() || amountCents <= 0) {
                throw new IllegalArgumentException("Nonblank id and positive cents are mandatory");
            }
            Objects.requireNonNull(currency, "currency");
        }
    }

    public record ReceiverCapabilities(Set<Format> supported) {
        public ReceiverCapabilities {
            supported = Set.copyOf(Objects.requireNonNull(supported, "supported"));
            if (supported.isEmpty()) {
                throw new IllegalArgumentException("At least one supported wire format is required");
            }
        }

        public static ReceiverCapabilities v1Only() {
            return new ReceiverCapabilities(Set.of(Format.V1));
        }

        public static ReceiverCapabilities v1AndV2() {
            return new ReceiverCapabilities(Set.of(Format.V1, Format.V2));
        }
    }

    /**
     * For fan-out messaging, EVERY recipient must understand the emitted message.
     * In a real broker this capability list must be maintained by a reliable
     * deployment/consumer registry; the broker does not negotiate it for us.
     */
    public static ReceiverCapabilities commonFormats(List<ReceiverCapabilities> receivers) {
        if (receivers == null || receivers.isEmpty()) {
            throw new IllegalArgumentException("Cannot publish without known recipients");
        }
        EnumSet<Format> intersection = EnumSet.allOf(Format.class);
        for (ReceiverCapabilities receiver : receivers) {
            intersection.retainAll(Objects.requireNonNull(receiver, "receiver").supported());
        }
        if (intersection.isEmpty()) {
            throw new IllegalStateException("No common wire format for all recipients");
        }
        return new ReceiverCapabilities(intersection);
    }

    /**
     * In V1 there was no currency field. The historical contract means EUR.
     * Sending USD as V1 would silently corrupt the business meaning.
     */
    public static Map<String, Object> legacyProducer(Order order) {
        Objects.requireNonNull(order, "order");
        if (order.currency() != Currency.EUR) {
            throw new IllegalStateException("V1 implicitly means EUR; cannot downgrade " + order.currency());
        }
        return Map.of("schemaVersion", 1, "orderId", order.id(),
                "amountCents", order.amountCents());
    }

    /** Raw V2 writer, used only when the recipients have explicitly adopted V2. */
    public static Map<String, Object> v2Producer(Order order) {
        Objects.requireNonNull(order, "order");
        return Map.of("schemaVersion", 2, "orderId", order.id(),
                "amountCents", order.amountCents(), "currency", order.currency().name());
    }

    /**
     * A cautious new producer prefers V1 until BOTH the deployment flag and
     * *every* recipient capability permit V2. No silent semantic downgrade.
     */
    public static Map<String, Object> stagedProducer(Order order,
                                                      ReceiverCapabilities receivers,
                                                      boolean v2Enabled) {
        Objects.requireNonNull(receivers, "receivers");
        if (v2Enabled && receivers.supported().contains(Format.V2)) {
            return v2Producer(order);
        }
        if (receivers.supported().contains(Format.V1)) {
            return legacyProducer(order);
        }
        throw new IllegalStateException("No enabled wire format supported by the recipients");
    }

    /** A deliberately strict old client, not a claim about every historical consumer. */
    public static Order legacyConsumer(Map<String, Object> payload) {
        requireFields(payload, Set.of("schemaVersion", "orderId", "amountCents"), 1);
        return new Order(readId(payload), readCents(payload), Currency.EUR);
    }

    /** Expand the reader FIRST: accept the old and the new representation. */
    public static Order upgradedConsumer(Map<String, Object> payload) {
        Objects.requireNonNull(payload, "payload");
        Object version = payload.get("schemaVersion");
        if (Integer.valueOf(1).equals(version)) {
            return legacyConsumer(payload);
        }
        requireFields(payload,
                Set.of("schemaVersion", "orderId", "amountCents", "currency"), 2);
        Object currency = payload.get("currency");
        if (!(currency instanceof String name)) {
            throw new IllegalArgumentException("V2 currency must be a string");
        }
        final Currency parsed;
        try {
            parsed = Currency.valueOf(name);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Unsupported currency: " + name, invalid);
        }
        return new Order(readId(payload), readCents(payload), parsed);
    }

    private static void requireFields(Map<String, Object> payload,
                                      Set<String> expected, int version) {
        Objects.requireNonNull(payload, "payload");
        if (!payload.keySet().equals(expected)
                || !Integer.valueOf(version).equals(payload.get("schemaVersion"))) {
            throw new IllegalArgumentException("Unexpected fields/version for V" + version);
        }
    }

    private static String readId(Map<String, Object> payload) {
        if (!(payload.get("orderId") instanceof String id) || id.isBlank()) {
            throw new IllegalArgumentException("Nonblank orderId required");
        }
        return id;
    }

    private static long readCents(Map<String, Object> payload) {
        Object amount = payload.get("amountCents");
        // Jackson untyped JSON parser emits Integer or Long for integral
        // numbers; reject doubles/decimal fractions rather than silently truncate.
        if (amount instanceof Integer number) {
            return number.longValue();
        }
        if (amount instanceof Long number) {
            return number;
        }
        throw new IllegalArgumentException("Integral amountCents required");
    }
}
