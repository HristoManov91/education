package bg.hristomanov.education.ai.claims;

import java.util.Objects;

public final class ApprovedClaimsPolicy {
    private static final long REVIEW_THRESHOLD_CENTS = 100_000;

    public Outcome assess(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        if (!claim.documentsComplete()) {
            return Outcome.NEED_DOCUMENTS;
        }
        // Самостоятелна policy промяна: SME е одобрил премахване на partner bypass.
        if (claim.suspectedFraud() || claim.amountCents() > REVIEW_THRESHOLD_CENTS) {
            return Outcome.MANUAL_REVIEW;
        }
        return Outcome.APPROVED;
    }
}
