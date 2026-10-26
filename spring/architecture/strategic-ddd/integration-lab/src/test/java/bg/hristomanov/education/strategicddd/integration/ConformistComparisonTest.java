package bg.hristomanov.education.strategicddd.integration;

import bg.hristomanov.education.strategicddd.claims.ClaimsEligibilityService;
import bg.hristomanov.education.strategicddd.claims.CoverageDecision;
import bg.hristomanov.education.strategicddd.contracts.ContractRegistry;
import bg.hristomanov.education.strategicddd.contracts.RegisteredVehicle;
import bg.hristomanov.education.strategicddd.integration.bad.NaiveConformistClaimsChecker;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ConformistComparisonTest {

    @Test
    void badConformistAndGoodAclReturnSameBusinessOutcomeButWithDifferentCoupling() {
        ContractRegistry contracts = new ContractRegistry();
        var snapshot = contracts.activate(
                "CONFORMIST-001",
                new RegisteredVehicle("WVWZZZ1JZXW000001", "CA1234AB", "Electric Touring"),
                "RETAIL", new BigDecimal("399.00"));

        // Direct use of ContractSnapshot/ContractStatus by a downstream consumer:
        boolean naiveEligible = new NaiveConformistClaimsChecker().isEligible(snapshot);

        // Claims' own model is insulated by the ACL (no imports from Contracts).
        CoverageDecision aclResult = new ClaimsEligibilityService(
                new ContractToClaimsAclAdapter(contracts)).assess("CONFORMIST-001");

        assertThat(naiveEligible).isTrue();
        assertThat(aclResult.status()).isEqualTo(
                CoverageDecision.Status.ELIGIBLE_FOR_REVIEW);

        // The test does not claim that an ACL is always better: if downstream
        // intentionally conforms to a stable upstream, the simpler design can win.
        // The difference is dependency and change ownership, not today's boolean.
    }
}
