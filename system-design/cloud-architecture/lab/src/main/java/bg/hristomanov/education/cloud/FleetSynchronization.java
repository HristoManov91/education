package bg.hristomanov.education.cloud;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** In-memory model: one owner, full snapshots, per-vehicle versions and bounded backlog.
 * Synchronization is only within this JVM; a real outbox uses durable DB transactions.
 */
public final class FleetSynchronization {
    private FleetSynchronization() {}

    // (vehicleId, version) is the event identity; empty location is a deletion tombstone.
    public record Event(String vehicleId, long version, Optional<String> location) {
        public Event {
            Objects.requireNonNull(vehicleId);
            Objects.requireNonNull(location);
            if (vehicleId.isBlank() || version <= 0 || location.filter(String::isBlank).isPresent()) {
                throw new IllegalArgumentException("Invalid event");
            }
        }
    }

    public static final class Source {
        private final int maxPending;
        private final Map<String, Event> state = new HashMap<>();
        private final ArrayDeque<Event> pending = new ArrayDeque<>();

        public Source(int maxPending) {
            if (maxPending < 1) throw new IllegalArgumentException("Positive capacity required");
            this.maxPending = maxPending;
        }

        public synchronized Event write(String id, Optional<String> location) {
            if (pending.size() >= maxPending) throw new IllegalStateException("Outbox full");
            var previous = state.get(id);
            var next = new Event(id, previous == null ? 1 : Math.incrementExact(previous.version()), location);
            state.put(id, next);
            pending.addLast(next);
            return next;
        }

        public synchronized Optional<Event> snapshot(String id) {
            return Optional.ofNullable(state.get(id));
        }
        public synchronized Optional<Event> next() { return Optional.ofNullable(pending.peekFirst()); }
        public synchronized int pendingCount() { return pending.size(); }

        public synchronized void acknowledge(Event event) {
            if (!Objects.equals(event, pending.peekFirst()) || event == null) {
                throw new IllegalStateException("ACK must match pending head");
            }
            pending.removeFirst();
        }
    }

    public enum ApplyResult { APPLIED, DUPLICATE, STALE }

    public static final class Projection {
        private final Map<String, Event> state = new HashMap<>();

        public synchronized ApplyResult apply(Event event) {
            Objects.requireNonNull(event);
            var current = state.get(event.vehicleId());
            if (current != null) {
                if (event.version() < current.version()) return ApplyResult.STALE;
                if (event.version() == current.version()) {
                    if (!event.equals(current)) throw new IllegalStateException("Conflicting same version");
                    return ApplyResult.DUPLICATE;
                }
            }
            // Full snapshot (including tombstone): newest version subsumes earlier snapshots.
            state.put(event.vehicleId(), event);
            return ApplyResult.APPLIED;
        }

        public synchronized Optional<Event> snapshot(String id) {
            return Optional.ofNullable(state.get(id));
        }
    }

    public enum Delivery { EMPTY, LINK_DOWN, ACK_LOST, ACKED }

    /** Single relay, simulated network outcomes. Production needs durable lease/offset handling. */
    public static Delivery relayNext(Source source, Projection projection, boolean linkUp, boolean loseAck) {
        var event = source.next();
        if (event.isEmpty()) return Delivery.EMPTY;
        if (!linkUp) return Delivery.LINK_DOWN;
        projection.apply(event.orElseThrow());
        if (loseAck) return Delivery.ACK_LOST;
        source.acknowledge(event.orElseThrow());
        return Delivery.ACKED;
    }
}
