package bg.hristomanov.education.loom.labs.observability;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NestedScopeThreadDumpDemoTest {

    @Test
    void nestedScopesKeepThreeLeafTasksAliveUntilReleasedAndJoinThem() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<List<String>> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread owner = Thread.ofVirtual().start(() -> {
            try {
                result.set(NestedScopeThreadDumpDemo.runScenario(started, release));
            } catch (Throwable error) {
                failure.set(error);
            }
        });

        try {
            assertTrue(started.await(10, TimeUnit.SECONDS),
                    "Both nested leaves and the sibling must be running");
            assertTrue(owner.isAlive(), "Owner must wait for child work");
        } finally {
            release.countDown();
            owner.join(10_000);
        }

        assertFalse(owner.isAlive(), "No structured child should keep the owner alive");
        assertNull(failure.get());
        assertEquals(List.of("profile+preferences", "order"), result.get());
    }
}
