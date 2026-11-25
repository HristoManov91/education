package bg.hristomanov.education.eval;

import dev.dokimos.core.Dataset;
import dev.dokimos.core.Example;
import dev.dokimos.core.Experiment;
import dev.dokimos.core.evaluators.ExactMatchEvaluator;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DokimosOfflineTest {
    @Test
    void realFrameworkScoresExactStructuredLabelsWithoutModel() {
        var dataset = Dataset.builder().name("fixture-exact-labels")
                .addExample(Example.of("venue", "HALL_A"))
                .addExample(Example.of("parking", "UNKNOWN")).build();
        // Fixed simulated adapter responses; no LLM invocation, no expectedOutput inside task.
        var observed = Map.of("venue", "HALL_A", "parking", "AVAILABLE");
        var result = Experiment.builder().name("exact-label-negative-control")
                .dataset(dataset).task(example -> Map.of("output", observed.get(example.input())))
                .evaluators(ExactMatchEvaluator.builder().build()).build().run();
        assertEquals(2, result.totalCount());
        assertEquals(1, result.passCount());
        assertEquals(1, result.failCount());
        assertEquals(.5, result.passRate(), 1e-12);
    }
}
