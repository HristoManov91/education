package bg.hristomanov.education.patterns.mastery.archive;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Observable in-memory test trail; NOT persistent compliance-grade audit. */
public final class AuditTrail {
    private final ConcurrentLinkedQueue<String> entries = new ConcurrentLinkedQueue<>();

    public void add(String entry) {
        entries.add(entry);
    }

    public List<String> entries() {
        return List.copyOf(entries);
    }
}
