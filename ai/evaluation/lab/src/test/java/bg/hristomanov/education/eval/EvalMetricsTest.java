package bg.hristomanov.education.eval;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EvalMetricsTest {
    @Test
    void labeledRetrievalMetricsAndUndefinedDenominator() {
        var score = EvalMetrics.retrieval(Set.of("java", "noise"), Set.of("java", "loom"));
        assertEquals(.5, score.precision().orElseThrow(), 1e-12);
        assertEquals(.5, score.recall().orElseThrow(), 1e-12);
        var empty = EvalMetrics.retrieval(Set.of(), Set.of());
        assertTrue(empty.precision().isEmpty());
        assertTrue(empty.recall().isEmpty());
        assertEquals(0, EvalMetrics.retrieval(Set.of(), Set.of("java")).recall().orElseThrow(), 1e-12);
    }
    @Test
    void humanAgreementAndFalsePassExposeBadJudge() {
        var c = EvalMetrics.confusion(List.of(true,false,true,false), List.of(true,true,true,false));
        assertEquals(new EvalMetrics.Confusion(2,1,1,0), c);
        assertEquals(.75, c.agreement().orElseThrow(), 1e-12);
        assertEquals(.5, c.falsePassRate().orElseThrow(), 1e-12);
        assertTrue(EvalMetrics.confusion(List.of(true), List.of(true)).falsePassRate().isEmpty());
    }
    @Test
    void sampleDeviationShowsRepeatabilityNotCorrectness() {
        assertEquals(0, EvalMetrics.sampleSd(List.of(.95,.95,.95)).orElseThrow(), 1e-12);
        assertEquals(Math.sqrt(.08), EvalMetrics.sampleSd(List.of(.4,.8)).orElseThrow(), 1e-12);
        assertTrue(EvalMetrics.sampleSd(List.of(.8)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> EvalMetrics.sampleSd(List.of(Double.NaN)));
    }
    @Test
    void empiricalAnyAndAllDoNotAssumeIndependence() {
        var r = EvalMetrics.empiricalTrials(List.of(List.of(true,true), List.of(true,false), List.of(false,false)));
        assertEquals(.5, r.perTrial(), 1e-12);
        assertEquals(2.0/3, r.anyPass(), 1e-12);
        assertEquals(1.0/3, r.allPass(), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> EvalMetrics.empiricalTrials(List.of(List.of(true), List.of(true,false))));
    }
    @Test
    void costBudgetCountsAllCallsAndRejectsOverflow() {
        assertEquals(900, EvalMetrics.plannedCalls(100,3,3));
        assertThrows(ArithmeticException.class, () -> EvalMetrics.plannedCalls(Long.MAX_VALUE,2,1));
    }
}
