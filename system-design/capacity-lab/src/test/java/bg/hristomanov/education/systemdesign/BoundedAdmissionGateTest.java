package bg.hristomanov.education.systemdesign;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class BoundedAdmissionGateTest {

    @Test void overloadRejectsSecondRequestWithoutExecutingWork() throws Exception {
        BoundedAdmissionGate gate = new BoundedAdmissionGate(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<BoundedAdmissionGate.Result<String>> firstResult = new AtomicReference<>();
        Thread first = Thread.ofVirtual().start(() -> firstResult.set(gate.execute(() -> {
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test deadline");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
            return "first";
        })));
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(gate.currentlyInFlight()).isEqualTo(1);
            AtomicInteger sideEffects = new AtomicInteger();

            BoundedAdmissionGate.Result<String> second = gate.execute(() -> {
                sideEffects.incrementAndGet();
                return "should-not-run";
            });

            assertThat(second).isEqualTo(new BoundedAdmissionGate.Rejected<>("AT_CAPACITY"));
            assertThat(sideEffects.get()).isZero();
            assertThat(gate.rejectedCount()).isEqualTo(1);
        } finally {
            release.countDown();
            first.join(5_000);
        }
        assertThat(first.isAlive()).isFalse();
        assertThat(firstResult.get()).isEqualTo(new BoundedAdmissionGate.Accepted<>("first"));
        assertThat(gate.currentlyInFlight()).isZero();
    }

    @Test void errorReleasesCapacityForNextRequest() {
        BoundedAdmissionGate gate = new BoundedAdmissionGate(1);
        assertThatThrownBy(() -> gate.execute(() -> {
            throw new IllegalStateException("failed upstream");
        })).isInstanceOf(IllegalStateException.class).hasMessage("failed upstream");
        assertThat(gate.currentlyInFlight()).isZero();
        assertThat(gate.execute(() -> "success"))
                .isEqualTo(new BoundedAdmissionGate.Accepted<>("success"));
    }

    @Test void refusesUnboundedConfigurationAndNullWork() {
        assertThatThrownBy(() -> new BoundedAdmissionGate(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BoundedAdmissionGate(1).execute(null))
                .isInstanceOf(NullPointerException.class);
    }
}
