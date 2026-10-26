package bg.hristomanov.education.strategicddd.claims;

import java.util.Optional;

/**
 * Downstream-owned contract. Implemented by an ACL or a replicated projection,
 * without leaking upstream DTOs, enums or persistence entities into Claims.
 */
public interface ContractFactsPort {

    Optional<ContractFacts> find(String agreementId);
}
