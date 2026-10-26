package bg.hristomanov.education.strategicddd.claims;

import java.util.Objects;

/**
 * Illustrative Claims decision. An active contract enables a claims review;
 * it does NOT guarantee real insurance coverage or payment.
 */
public final class ClaimsEligibilityService {

    private final ContractFactsPort contractFacts;

    public ClaimsEligibilityService(ContractFactsPort contractFacts) {
        this.contractFacts = Objects.requireNonNull(contractFacts, "contractFacts");
    }

    public CoverageDecision assess(String agreementId) {
        if (agreementId == null || agreementId.isBlank()) {
            throw new IllegalArgumentException("agreementId required");
        }

        return contractFacts.find(agreementId)
                .map(facts -> facts.contractActive()
                        ? new CoverageDecision(agreementId,
                                CoverageDecision.Status.ELIGIBLE_FOR_REVIEW,
                                facts.coveredAssetVin())
                        : new CoverageDecision(agreementId,
                                CoverageDecision.Status.CONTRACT_NOT_ACTIVE, null))
                .orElseGet(() -> new CoverageDecision(agreementId,
                        CoverageDecision.Status.CONTRACT_NOT_FOUND, null));
    }
}
