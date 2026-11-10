package bg.hristomanov.education.kubernetes;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkControllerTest {
    @Test
    void boundedSimulationCompletesWithoutArtificialDelay() throws Exception {
        var result = new WorkController().work(0);
        assertThat(result.status()).isEqualTo("completed");
        assertThat(result.delayMs()).isZero();
    }

    @Test
    void rejectsNegativeAndUnboundedDelays() {
        var controller = new WorkController();
        assertThatThrownBy(() -> controller.work(-1)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.work(10001)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void propagatesInterruptionRatherThanSilentlySwallowingCancellation() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> new WorkController().work(100))
                    .isInstanceOf(InterruptedException.class);
        } finally {
            // InterruptedException clears the flag; always restore a clean JUnit worker.
            Thread.interrupted();
        }
    }
}
