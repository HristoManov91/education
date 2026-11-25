package bg.hristomanov.education.cloud;

import org.junit.jupiter.api.Test;
import java.util.Optional;

import static bg.hristomanov.education.cloud.FleetSynchronization.*;
import static org.junit.jupiter.api.Assertions.*;

class FleetSynchronizationTest {
    @Test
    void ownerContinuesWithinBoundedBacklogAndReplaysAfterPartition() {
        var source = new Source(3);
        var projection = new Projection();
        var first = source.write("car", Optional.of("Sofia"));
        assertEquals(Delivery.LINK_DOWN, relayNext(source, projection, false, false));
        var second = source.write("car", Optional.of("Plovdiv"));
        assertEquals(second, source.snapshot("car").orElseThrow());
        assertTrue(projection.snapshot("car").isEmpty());
        assertEquals(2, source.pendingCount());
        assertEquals(Delivery.ACKED, relayNext(source, projection, true, false));
        assertEquals(first, projection.snapshot("car").orElseThrow());
        assertEquals(Delivery.ACKED, relayNext(source, projection, true, false));
        assertEquals(second, projection.snapshot("car").orElseThrow());
        assertEquals(Delivery.EMPTY, relayNext(source, projection, true, false));
    }

    @Test
    void lostAckLeavesEventPendingAndReplayIsIdempotent() {
        var source = new Source(2);
        var projection = new Projection();
        var event = source.write("car", Optional.of("Sofia"));
        assertEquals(Delivery.ACK_LOST, relayNext(source, projection, true, true));
        assertEquals(1, source.pendingCount());
        assertEquals(event, projection.snapshot("car").orElseThrow());
        assertEquals(ApplyResult.DUPLICATE, projection.apply(event));
        assertEquals(Delivery.ACKED, relayNext(source, projection, true, false));
        assertEquals(0, source.pendingCount());
        assertEquals(event, projection.snapshot("car").orElseThrow());
    }

    @Test
    void outOfOrderFullSnapshotCannotOverwriteNewerState() {
        var projection = new Projection();
        var newest = new Event("car", 3, Optional.of("Varna"));
        assertEquals(ApplyResult.APPLIED, projection.apply(newest));
        assertEquals(ApplyResult.STALE, projection.apply(new Event("car", 2, Optional.of("Sofia"))));
        assertEquals(newest, projection.snapshot("car").orElseThrow());
    }

    @Test
    void tombstoneStopsOldMessageFromResurrectingVehicle() {
        var source = new Source(3);
        var old = source.write("car", Optional.of("Sofia"));
        var deleted = source.write("car", Optional.empty());
        var projection = new Projection();
        projection.apply(deleted);
        assertEquals(ApplyResult.STALE, projection.apply(old));
        assertTrue(projection.snapshot("car").orElseThrow().location().isEmpty());
        assertEquals(3, source.write("car", Optional.of("Varna")).version());
    }

    @Test
    void conflictingSameVersionIsRejected() {
        var projection = new Projection();
        projection.apply(new Event("car", 1, Optional.of("Sofia")));
        assertThrows(IllegalStateException.class,
                () -> projection.apply(new Event("car", 1, Optional.of("Varna"))));
        assertEquals("Sofia", projection.snapshot("car").orElseThrow().location().orElseThrow());
    }

    @Test
    void fullOutboxRejectsBeforeChangingOwnerState() {
        var source = new Source(1);
        var first = source.write("car", Optional.of("Sofia"));
        assertThrows(IllegalStateException.class, () -> source.write("car", Optional.of("Varna")));
        assertEquals(first, source.snapshot("car").orElseThrow());
        assertEquals(first, source.next().orElseThrow());
        assertEquals(1, source.pendingCount());
    }

    @Test
    void invalidAckCannotRemoveAnUnrelatedPendingEvent() {
        var source = new Source(2);
        var first = source.write("car", Optional.of("Sofia"));
        var second = source.write("other", Optional.of("Varna"));
        assertThrows(IllegalStateException.class, () -> source.acknowledge(second));
        assertEquals(first, source.next().orElseThrow());
        assertEquals(2, source.pendingCount());
    }

    @Test
    void versionsArePerVehicleAndInvalidEventDoesNotEnqueue() {
        var source = new Source(3);
        assertEquals(1, source.write("a", Optional.of("Sofia")).version());
        assertEquals(1, source.write("b", Optional.of("Varna")).version());
        assertThrows(IllegalArgumentException.class, () -> source.write("", Optional.empty()));
        assertEquals(2, source.pendingCount());
    }
}
