package bg.hristomanov.education.strategicddd.integration;

import bg.hristomanov.education.strategicddd.claims.ClaimsEligibilityService;
import bg.hristomanov.education.strategicddd.claims.ContractFacts;
import bg.hristomanov.education.strategicddd.claims.CoverageDecision;
import bg.hristomanov.education.strategicddd.contracts.ContractRegistry;
import bg.hristomanov.education.strategicddd.contracts.ContractSnapshot;
import bg.hristomanov.education.strategicddd.contracts.RegisteredVehicle;
import bg.hristomanov.education.strategicddd.quoting.LeaseQuote;
import bg.hristomanov.education.strategicddd.quoting.QuotingService;
import bg.hristomanov.education.strategicddd.quoting.VehicleConfiguration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategicDddScenarioTest {

    private static final String VIN = "WVWZZZ1JZXW000001";

    @Test
    void quotingCanCalculateVehicleConfigurationWithoutVinOrContract() {
        QuotingService quoting = new QuotingService();

        LeaseQuote quote = quoting.quote(new VehicleConfiguration(
                "EV-TOURING", Set.of("PANORAMA", "WINTER")));

        assertThat(quote.configuration().modelCode()).isEqualTo("EV-TOURING");
        assertThat(quote.monthlyEur()).isEqualByComparingTo("462.00");
        // A vehicle in Quoting represents an offerable configuration, not an asset.
        assertThat(quote.configuration().optionCodes()).containsExactlyInAnyOrder(
                "PANORAMA", "WINTER");
    }

    @Test
    void quoteRejectsInvalidOptionRatherThanBorrowingPhysicalVehicleRules() {
        QuotingService quoting = new QuotingService();

        assertThatThrownBy(() -> quoting.quote(
                new VehicleConfiguration("EV-TOURING", Set.of("UNKNOWN_OPTION"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Option not offered");

        assertThatThrownBy(() -> new RegisteredVehicle("NOT_A_VALID_VIN",
                "CA1234AB", "Electric Touring"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VIN");
    }

    @Test
    void aclTranslatesContractStatusIntoClaimsOwnedVocabulary() {
        ContractRegistry contracts = new ContractRegistry();
        ClaimsEligibilityService claims = new ClaimsEligibilityService(
                new ContractToClaimsAclAdapter(contracts));

        assertThat(claims.assess("AGREEMENT-42").status())
                .isEqualTo(CoverageDecision.Status.CONTRACT_NOT_FOUND);

        contracts.activate("AGREEMENT-42", vehicle("CA1234AB"),
                "RETAIL", new BigDecimal("462.00"));

        CoverageDecision before = claims.assess("AGREEMENT-42");
        assertThat(before.status()).isEqualTo(CoverageDecision.Status.ELIGIBLE_FOR_REVIEW);
        assertThat(before.assetVin()).isEqualTo(VIN);

        contracts.terminate("AGREEMENT-42");

        CoverageDecision after = claims.assess("AGREEMENT-42");
        assertThat(after.status()).isEqualTo(CoverageDecision.Status.CONTRACT_NOT_ACTIVE);
        assertThat(after.assetVin()).isNull();
    }

    @Test
    void registrationPlateChangeDoesNotChangeClaimsDecisionOrItsOwnedModel() {
        ContractRegistry contracts = new ContractRegistry();
        contracts.activate("AGREEMENT-PLATE", vehicle("CA1234AB"),
                "FLEET", new BigDecimal("600.00"));
        ClaimsEligibilityService claims = new ClaimsEligibilityService(
                new ContractToClaimsAclAdapter(contracts));

        ContractFacts before = new ContractToClaimsAclAdapter(contracts)
                .find("AGREEMENT-PLATE").orElseThrow();

        contracts.changeRegistrationPlate("AGREEMENT-PLATE", "CA4321BA");

        ContractFacts after = new ContractToClaimsAclAdapter(contracts)
                .find("AGREEMENT-PLATE").orElseThrow();
        assertThat(after.coveredAssetVin()).isEqualTo(before.coveredAssetVin());
        assertThat(after.contractActive()).isTrue();
        assertThat(after.upstreamRevision()).isGreaterThan(before.upstreamRevision());
        assertThat(claims.assess("AGREEMENT-PLATE").status())
                .isEqualTo(CoverageDecision.Status.ELIGIBLE_FOR_REVIEW);
    }

    @Test
    void replicatedProjectionHasAnExplicitStaleReadWindow() {
        ContractRegistry contracts = new ContractRegistry();
        ReplicatedContractFactsProjection projection = new ReplicatedContractFactsProjection();
        ClaimsEligibilityService claims = new ClaimsEligibilityService(projection);

        ContractSnapshot created = contracts.activate("AGREEMENT-SYNC",
                vehicle("CA1234AB"), "RETAIL", new BigDecimal("259.00"));

        // No event has reached the Claims projection yet.
        assertThat(claims.assess("AGREEMENT-SYNC").status())
                .isEqualTo(CoverageDecision.Status.CONTRACT_NOT_FOUND);

        projection.acceptPublishedContract(created);
        assertThat(claims.assess("AGREEMENT-SYNC").status())
                .isEqualTo(CoverageDecision.Status.ELIGIBLE_FOR_REVIEW);

        ContractSnapshot terminated = contracts.terminate("AGREEMENT-SYNC");
        // Demonstrates the cost of asynchronous data duplication: stale reads.
        assertThat(claims.assess("AGREEMENT-SYNC").status())
                .isEqualTo(CoverageDecision.Status.ELIGIBLE_FOR_REVIEW);

        projection.acceptPublishedContract(terminated);
        assertThat(claims.assess("AGREEMENT-SYNC").status())
                .isEqualTo(CoverageDecision.Status.CONTRACT_NOT_ACTIVE);
    }

    @Test
    void staleAndDuplicateEventsCannotUndoANewerPublishedFact() {
        ContractRegistry contracts = new ContractRegistry();
        ReplicatedContractFactsProjection projection = new ReplicatedContractFactsProjection();

        ContractSnapshot first = contracts.activate("AGREEMENT-ORDER",
                vehicle("CA1234AB"), "RETAIL", new BigDecimal("300.00"));
        ContractSnapshot second = contracts.terminate("AGREEMENT-ORDER");

        projection.acceptPublishedContract(second);
        projection.acceptPublishedContract(first);   // out of order
        projection.acceptPublishedContract(second);  // duplicate

        ContractFacts current = projection.find("AGREEMENT-ORDER").orElseThrow();
        assertThat(current.contractActive()).isFalse();
        assertThat(current.upstreamRevision()).isEqualTo(second.revision());
    }

    @Test
    void claimsCanReadLocalProjectionWithoutRuntimeCallsToContractService() {
        ContractRegistry registry = new ContractRegistry();
        ContractSnapshot published = registry.activate(
                "AGREEMENT-OFFLINE", vehicle("CA1234AB"), "FLEET",
                new BigDecimal("600.00"));

        ReplicatedContractFactsProjection claimsOwnedReadStore =
                new ReplicatedContractFactsProjection();
        claimsOwnedReadStore.acceptPublishedContract(published);

        // The consumer only uses the local projection, not the ContractRegistry.
        ClaimsEligibilityService claims = new ClaimsEligibilityService(claimsOwnedReadStore);
        assertThat(claims.assess("AGREEMENT-OFFLINE").status())
                .isEqualTo(CoverageDecision.Status.ELIGIBLE_FOR_REVIEW);
    }

    @Test
    void contractRegistryRejectsDuplicateAgreementAndPreservesItsRevisionOnRepeatedTermination() {
        ContractRegistry registry = new ContractRegistry();
        registry.activate("AGREEMENT-UNIQUE", vehicle("CA1234AB"),
                "RETAIL", new BigDecimal("399.00"));

        assertThatThrownBy(() -> registry.activate(
                "AGREEMENT-UNIQUE", vehicle("CA1234AB"), "RETAIL",
                new BigDecimal("399.00")))
                .isInstanceOf(IllegalStateException.class);

        ContractSnapshot firstTermination = registry.terminate("AGREEMENT-UNIQUE");
        ContractSnapshot retry = registry.terminate("AGREEMENT-UNIQUE");
        assertThat(retry.revision()).isEqualTo(firstTermination.revision());
    }

    private static RegisteredVehicle vehicle(String plate) {
        return new RegisteredVehicle(VIN, plate, "Electric Touring");
    }
}
