package bg.hristomanov.education.integrationpatterns.queue;

import java.util.Objects;

/**
 * A tenant belongs to one independent cell. This only models placement:
 * fault isolation requires separate real queues AND separate consumer capacity.
 * Resizing cellCount changes tenant placement and needs a migration plan.
 */
public final class CellRouter {
    private final int cellCount;

    public CellRouter(int cellCount) {
        if (cellCount <= 0) {
            throw new IllegalArgumentException("cellCount must be positive");
        }
        this.cellCount = cellCount;
    }

    public int cellFor(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        return Math.floorMod(tenantId.hashCode(), cellCount);
    }

    public int cellCount() {
        return cellCount;
    }
}
