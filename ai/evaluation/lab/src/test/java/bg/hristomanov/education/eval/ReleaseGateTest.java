package bg.hristomanov.education.eval;

import org.junit.jupiter.api.Test;
import java.util.*;
import static bg.hristomanov.education.eval.ReleaseGate.*;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseGateTest {
    private static final Map<String,String> REQUIRED = Map.of("faq","facts","guard","security");
    private static final Map<String,Double> BASELINE = Map.of("facts",.9,"security",.9);
    private Decision decide(List<Measurement> results, long calls) {
        return ReleaseGate.decide(REQUIRED, results, BASELINE, .8, .05, 1, calls, 100);
    }
    @Test
    void allRequiredCasesAndSlicesPass() {
        assertTrue(decide(List.of(new Measurement("faq",true,.9),new Measurement("guard",true,.9)),100).allowed());
    }
    @Test
    void securityFailureBlocksEvenPerfectQuality() {
        var d = decide(List.of(new Measurement("faq",true,1.0),new Measurement("guard",false,1.0)),100);
        assertFalse(d.allowed());
        assertTrue(d.reasons().contains("HARD:guard"));
    }
    @Test
    void noAveragingAwayARegressedSlice() {
        var d = decide(List.of(new Measurement("faq",true,1.0),new Measurement("guard",true,.81)),100);
        assertTrue(d.reasons().contains("REGRESSION:security"));
    }
    @Test
    void missingDuplicateUnexpectedOrInvalidScoreBlocks() {
        var d = decide(List.of(new Measurement("faq",true,null),new Measurement("faq",true,1.0),new Measurement("extra",true,1.0)),100);
        assertTrue(d.reasons().containsAll(List.of("MISSING:guard","INVALID_SCORE:faq","DUPLICATE:faq","UNEXPECTED:extra")));
    }
    @Test
    void budgetAndInsufficientSampleBlock() {
        var d = ReleaseGate.decide(REQUIRED,List.of(new Measurement("faq",true,.9),new Measurement("guard",true,.9)),
                BASELINE,.8,.05,2,101,100);
        assertTrue(d.reasons().containsAll(List.of("BUDGET","INSUFFICIENT:facts","INSUFFICIENT:security")));
    }
}
