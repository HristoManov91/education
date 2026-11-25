package bg.hristomanov.education.ai.claims;

import java.util.Objects;

public final class LegacyClaimsService {
    // Историческият bypass е observed behavior, не потвърдена бизнес политика.
    public Outcome assess(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        if (claim.documentsComplete()) {
            if (!claim.suspectedFraud()) {
                if (claim.amountCents() > 100_000) {
                    if (claim.preferredPartner()) {
                        return Outcome.APPROVED;
                    } else {
                        return Outcome.MANUAL_REVIEW;
                    }
                } else {
                    return Outcome.APPROVED;
                }
            } else {
                return Outcome.MANUAL_REVIEW;
            }
        } else {
            return Outcome.NEED_DOCUMENTS;
        }
    }
}
