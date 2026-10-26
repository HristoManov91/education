package bg.hristomanov.education.strategicddd.claims;

import java.util.Objects;

/**
 * Claims' OWN vocabulary: is the asset eligible for the illustrative
 * assessment? Not the full upstream ContractSnapshot schema.
 */
public record ContractFacts(
        String agreementId,
        String coveredAssetVin,
        boolean contractActive,
        long upstreamRevision) {

    public ContractFacts {
        Objects.requireNonNull(agreementId, "agreementId");
        Objects.requireNonNull(coveredAssetVin, "coveredAssetVin");
        if (agreementId.isBlank() || coveredAssetVin.isBlank() || upstreamRevision < 1) {
            throw new IllegalArgumentException("Invalid claims contract facts");
        }
    }
}
