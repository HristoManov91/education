package bg.hristomanov.education.instrumentation.benchmark;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentStartupComparisonTest {

    @Test
    void calculatesMedianWithoutAveragingOrReorderingInput() {
        List<Long> samples = List.of(50L, 10L, 20L, 40L);
        assertEquals(30L, AgentStartupComparison.median(samples));
        assertEquals(List.of(50L, 10L, 20L, 40L), samples);
        assertEquals(20L, AgentStartupComparison.median(List.of(30L, 10L, 20L)));
    }

    @Test
    void rejectsEmptySamples() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentStartupComparison.median(List.of()));
    }
}
