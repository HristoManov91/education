package bg.hristomanov.education.patterns.advanced.nullobject;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Null Object is legitimate only when auditing is OPTIONAL.
 * Security/compliance audit MUST NOT be silently replaced with NoOp.
 */
public interface AuditSink {
    void record(AuditEntry event);

    record AuditEntry(String operation, String reference) {
        public AuditEntry {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(reference, "reference");
        }
    }

    enum NoOp implements AuditSink {
        INSTANCE;
        @Override public void record(AuditEntry event) {
            Objects.requireNonNull(event, "event");
        }
    }

    final class InMemory implements AuditSink {
        private final ConcurrentLinkedQueue<AuditEntry> events = new ConcurrentLinkedQueue<>();

        @Override public void record(AuditEntry event) {
            events.add(Objects.requireNonNull(event, "event"));
        }

        public List<AuditEntry> events() {
            return List.copyOf(events);
        }
    }
}
