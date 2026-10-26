package bg.hristomanov.education.strategicddd.claims;

import java.util.Objects;

public record CoverageDecision(String agreementId, Status status, String assetVin) {

    public enum Status {
        ELIGIBLE_FOR_REVIEW,
        CONTRACT_NOT_ACTIVE,
        CONTRACT_NOT_FOUND
    }

    public CoverageDecision {
        Objects.requireNonNull(agreementId, "agreementId");
        Objects.requireNonNull(status, "status");
        if (status == Status.ELIGIBLE_FOR_REVIEW && (assetVin == null || assetVin.isBlank())) {
            throw new IllegalArgumentException("Eligible assessment must identify the asset");
        }
    }
}
