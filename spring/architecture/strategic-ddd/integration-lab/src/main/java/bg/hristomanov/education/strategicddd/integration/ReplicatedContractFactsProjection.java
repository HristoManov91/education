package bg.hristomanov.education.strategicddd.integration;

import bg.hristomanov.education.strategicddd.claims.ContractFacts;
import bg.hristomanov.education.strategicddd.claims.ContractFactsPort;
import bg.hristomanov.education.strategicddd.contracts.ContractSnapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Alternative to synchronous Contracts queries: a Claims-owned read projection
 * containing ONLY the facts it needs, translated by the ACL.
 *
 * This model demonstrates eventual consistency and duplicate/out-of-order
 * handling using upstream revision. It is NOT a durable consumer or Outbox/Inbox.
 */
public final class ReplicatedContractFactsProjection implements ContractFactsPort {

    private final ConcurrentMap<String, ContractFacts> byAgreementId = new ConcurrentHashMap<>();

    public void acceptPublishedContract(ContractSnapshot event) {
        Objects.requireNonNull(event, "event");
        ContractFacts incoming = ContractToClaimsAclAdapter.translate(event);
        byAgreementId.compute(incoming.agreementId(), (key, known) ->
                known == null || incoming.upstreamRevision() > known.upstreamRevision()
                        ? incoming
                        : known);
    }

    @Override
    public Optional<ContractFacts> find(String agreementId) {
        return Optional.ofNullable(byAgreementId.get(agreementId));
    }
}
