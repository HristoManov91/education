package bg.hristomanov.education.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.stream.Stream;
import static bg.hristomanov.education.eval.EvalModel.*;
import static org.junit.jupiter.api.Assertions.*;

class EvalHarnessTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final GoldenDataset GOLDEN = load("/golden-v1.json", GoldenDataset.class);
    private static final ReplayDataset REPLAY = load("/replay-v1.json", ReplayDataset.class);

    private static <T> T load(String file, Class<T> type) {
        try (var input = EvalHarnessTest.class.getResourceAsStream(file)) {
            return MAPPER.readValue(Objects.requireNonNull(input, file), type);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load fixture " + file, e);
        }
    }
    private static EvalCase find(String id) {
        return GOLDEN.cases().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }
    static Stream<Trace> traces() { return REPLAY.traces().stream(); }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("traces")
    void frozenReplayDetectsBadBehavior(Trace trace) {
        var result = EvalChecks.evaluate(find(trace.caseId()), trace);
        assertEquals("good".equals(trace.variant()), result.passed(), result.violations().toString());
    }

    @Test
    void allCasesHaveIndependentGoodAndBadControlsAndKnownSplit() {
        assertEquals("golden-v1", GOLDEN.version());
        assertEquals("replay-v1", REPLAY.version());
        assertEquals(10, GOLDEN.cases().size());
        assertEquals(20, REPLAY.traces().size());
        for (var c : GOLDEN.cases()) {
            assertEquals(1, REPLAY.traces().stream().filter(t -> t.caseId().equals(c.id()) && t.variant().equals("good")).count());
            assertEquals(1, REPLAY.traces().stream().filter(t -> t.caseId().equals(c.id()) && t.variant().equals("bad")).count());
        }
    }

    @Test
    void highQualityCannotHideOverlapOrUnsupportedParking() {
        var overlap = bad("overlap");
        assertTrue(overlap.quality() > .95);
        assertTrue(EvalChecks.evaluate(find("overlap"), overlap).violations().contains("OVERLAP"));
        assertTrue(EvalChecks.evaluate(find("parking-unknown"), bad("parking-unknown")).violations().contains("UNSUPPORTED_CLAIM"));
    }

    @Test
    void finalStateAndToolEffectAreCheckedSeparately() {
        assertTrue(EvalChecks.evaluate(find("confirmed-add"), bad("confirmed-add")).violations().contains("MISSING_REQUIRED_CALL"));
        assertTrue(EvalChecks.evaluate(find("multi-turn"), bad("multi-turn")).violations().contains("TRACE_STATE_MISMATCH"));
        assertTrue(EvalChecks.evaluate(find("future-only"), bad("future-only")).violations().contains("PAST_OR_UNKNOWN_RECOMMENDATION"));
        assertTrue(EvalChecks.evaluate(find("other-owner"), bad("other-owner")).violations().contains("CROSS_OWNER_READ"));
    }

    private static Trace bad(String id) {
        return REPLAY.traces().stream().filter(t -> t.caseId().equals(id) && t.variant().equals("bad")).findFirst().orElseThrow();
    }
}
