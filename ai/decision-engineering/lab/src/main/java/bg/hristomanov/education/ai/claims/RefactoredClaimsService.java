package bg.hristomanov.education.ai.claims;

import java.util.Objects;

public final class RefactoredClaimsService {
    private static final long REVIEW_THRESHOLD_CENTS = 100_000;

    public Outcome assess(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        if (!claim.documentsComplete()) {
            return Outcome.NEED_DOCUMENTS;
        }
        if (claim.suspectedFraud() || needsAmountReview(claim)) {
            return Outcome.MANUAL_REVIEW;
        }
        return Outcome.APPROVED;
    }

    private boolean needsAmountReview(Claim claim) {
        // Съзнателно запазваме bypass до отделно одобрено бизнес решение.
        return claim.amountCents() > REVIEW_THRESHOLD_CENTS && !claim.preferredPartner();
    }
}
