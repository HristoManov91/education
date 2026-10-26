package bg.hristomanov.education.strategicddd.integration;

import bg.hristomanov.education.strategicddd.claims.ClaimsEligibilityService;
import bg.hristomanov.education.strategicddd.claims.CoverageDecision;
import bg.hristomanov.education.strategicddd.contracts.ContractRegistry;
import bg.hristomanov.education.strategicddd.contracts.RegisteredVehicle;
import bg.hristomanov.education.strategicddd.quoting.QuotingService;
import bg.hristomanov.education.strategicddd.quoting.VehicleConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StrategicDddApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SpringWiringIntegrationTest {

    @Autowired
    QuotingService quoting;

    @Autowired
    ContractRegistry contracts;

    @Autowired
    ClaimsEligibilityService claims;

    @Test
    void oneDeployableSpringBootApplicationWiresThreeIndependentModels() {
        assertThat(quoting.quote(new VehicleConfiguration(
                "CITY-HYBRID", Set.of("SAFETY"))).monthlyEur())
                .isEqualByComparingTo("278.00");

        String agreementId = "SPRING-CONTEXT-01";
        contracts.activate(agreementId,
                new RegisteredVehicle("WVWZZZ1JZXW000001", "CA1234AB", "City Hybrid"),
                "RETAIL", new BigDecimal("278.00"));

        assertThat(claims.assess(agreementId).status())
                .isEqualTo(CoverageDecision.Status.ELIGIBLE_FOR_REVIEW);
    }
}
