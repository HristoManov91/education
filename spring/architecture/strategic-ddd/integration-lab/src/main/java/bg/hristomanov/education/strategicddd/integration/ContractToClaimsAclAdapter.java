package bg.hristomanov.education.strategicddd.integration;

import bg.hristomanov.education.strategicddd.claims.ContractFacts;
import bg.hristomanov.education.strategicddd.claims.ContractFactsPort;
import bg.hristomanov.education.strategicddd.contracts.ContractRegistry;
import bg.hristomanov.education.strategicddd.contracts.ContractSnapshot;
import bg.hristomanov.education.strategicddd.contracts.ContractStatus;

import java.util.Objects;
import java.util.Optional;

/**
 * Explicit Anti-Corruption Layer owned at the integration boundary:
 * upstream Contracts speaks in ContractSnapshot/ContractStatus,
 * downstream Claims speaks in ContractFacts/contractActive.
 *
 * This adapter uses an in-process call for a reproducible lab. For separate
 * deployments replace the reader with an HTTP client or an event projection
 * without changing the Claims domain API.
 */
public final class ContractToClaimsAclAdapter implements ContractFactsPort {

    private final ContractRegistry contracts;

    public ContractToClaimsAclAdapter(ContractRegistry contracts) {
        this.contracts = Objects.requireNonNull(contracts, "contracts");
    }

    @Override
    public Optional<ContractFacts> find(String agreementId) {
        return contracts.find(agreementId).map(ContractToClaimsAclAdapter::translate);
    }

    static ContractFacts translate(ContractSnapshot upstream) {
        return new ContractFacts(
                upstream.agreementId(),
                upstream.registeredVehicle().vin(),
                upstream.status() == ContractStatus.ACTIVE,
                upstream.revision());
    }
}
