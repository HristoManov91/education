package bg.hristomanov.education.ai.claims;

import org.junit.jupiter.api.Test;

import static bg.hristomanov.education.ai.claims.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

class ClaimsModernizationTest {
    private final LegacyClaimsService legacy = new LegacyClaimsService();
    private final RefactoredClaimsService refactor = new RefactoredClaimsService();
    private final ApprovedClaimsPolicy approved = new ApprovedClaimsPolicy();

    @Test
    void frozenObservedFixturesPinThresholdBypassAndPrecedence() {
        observed(new Claim(0, false, true, false), APPROVED);
        observed(new Claim(100_000, false, true, false), APPROVED);
        observed(new Claim(100_001, false, true, false), MANUAL_REVIEW);
        observed(new Claim(100_001, true, true, false), APPROVED);
        observed(new Claim(100_001, true, true, true), MANUAL_REVIEW);
        observed(new Claim(100_001, true, false, true), NEED_DOCUMENTS);
        observed(new Claim(Long.MAX_VALUE, false, true, false), MANUAL_REVIEW);
    }

    private void observed(Claim input, Outcome expected) {
        assertEquals(expected, legacy.assess(input), "legacy fixture: " + input);
        assertEquals(expected, refactor.assess(input), "refactor fixture: " + input);
    }

    @Test
    void refactorPreservesObservedBehaviorAcrossBoundaryMatrix() {
        for (long amount : amounts()) {
            for (boolean partner : flags()) {
                for (boolean docs : flags()) {
                    for (boolean fraud : flags()) {
                        var claim = new Claim(amount, partner, docs, fraud);
                        assertEquals(legacy.assess(claim), refactor.assess(claim), claim.toString());
                    }
                }
            }
        }
    }

    @Test
    void independentlySpecifiedApprovedPolicyFixtures() {
        assertEquals(NEED_DOCUMENTS, approved.assess(new Claim(100_001, true, false, true)));
        assertEquals(MANUAL_REVIEW, approved.assess(new Claim(0, true, true, true)));
        assertEquals(APPROVED, approved.assess(new Claim(100_000, true, true, false)));
        assertEquals(APPROVED, approved.assess(new Claim(100_000, false, true, false)));
        assertEquals(MANUAL_REVIEW, approved.assess(new Claim(100_001, true, true, false)));
        assertEquals(MANUAL_REVIEW, approved.assess(new Claim(100_001, false, true, false)));
        assertEquals(MANUAL_REVIEW, approved.assess(new Claim(Long.MAX_VALUE, true, true, false)));
    }

    @Test
    void policyChangeIsNotARefactorCounterexample() {
        var disputed = new Claim(100_001, true, true, false);
        assertEquals(APPROVED, legacy.assess(disputed));
        assertEquals(APPROVED, refactor.assess(disputed));
        assertEquals(MANUAL_REVIEW, approved.assess(disputed));
        assertNotEquals(refactor.assess(disputed), approved.assess(disputed));
    }

    @Test
    void approvedChangeAffectsOnlyExplicitlyApprovedSubset() {
        for (long amount : amounts()) {
            for (boolean partner : flags()) {
                for (boolean docs : flags()) {
                    for (boolean fraud : flags()) {
                        var claim = new Claim(amount, partner, docs, fraud);
                        boolean approvedChange = amount > 100_000 && partner && docs && !fraud;
                        if (approvedChange) {
                            assertEquals(APPROVED, legacy.assess(claim));
                            assertEquals(MANUAL_REVIEW, approved.assess(claim));
                        } else {
                            assertEquals(legacy.assess(claim), approved.assess(claim), claim.toString());
                        }
                    }
                }
            }
        }
    }

    @Test
    void negativeAmountsAndNullInputsRemainInvalid() {
        assertThrows(IllegalArgumentException.class, () -> new Claim(-1, false, true, false));
        assertThrows(NullPointerException.class, () -> legacy.assess(null));
        assertThrows(NullPointerException.class, () -> refactor.assess(null));
        assertThrows(NullPointerException.class, () -> approved.assess(null));
    }

    private static long[] amounts() {
        return new long[]{0, 1, 99_999, 100_000, 100_001, Long.MAX_VALUE};
    }

    private static boolean[] flags() {
        return new boolean[]{false, true};
    }
}
