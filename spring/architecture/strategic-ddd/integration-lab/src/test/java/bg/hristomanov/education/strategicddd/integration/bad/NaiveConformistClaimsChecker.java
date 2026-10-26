package bg.hristomanov.education.strategicddd.integration.bad;

import bg.hristomanov.education.strategicddd.contracts.ContractSnapshot;
import bg.hristomanov.education.strategicddd.contracts.ContractStatus;

/**
 * BAD / CONFORMIST (test-only): the downstream directly uses upstream types.
 * Any upstream enum/schema change leaks across this boundary.
 * Do not copy this into claims-domain.
 */
public final class NaiveConformistClaimsChecker {

    public boolean isEligible(ContractSnapshot upstreamContractModel) {
        return upstreamContractModel.status() == ContractStatus.ACTIVE;
    }
}
