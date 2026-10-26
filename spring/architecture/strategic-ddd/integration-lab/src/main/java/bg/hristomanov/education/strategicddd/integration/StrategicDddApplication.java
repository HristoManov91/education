package bg.hristomanov.education.strategicddd.integration;

import bg.hristomanov.education.strategicddd.claims.ClaimsEligibilityService;
import bg.hristomanov.education.strategicddd.claims.ContractFactsPort;
import bg.hristomanov.education.strategicddd.contracts.ContractRegistry;
import bg.hristomanov.education.strategicddd.quoting.QuotingService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * One Spring Boot DEPLOYMENT containing three distinct domain model boundaries.
 * Dependency direction is enforced by independent Maven domain artifacts;
 * runtime composition does NOT make their models the same bounded context.
 */
@SpringBootApplication
public class StrategicDddApplication {

    public static void main(String[] args) {
        SpringApplication.run(StrategicDddApplication.class, args);
    }

    @Bean
    QuotingService quotingService() {
        return new QuotingService();
    }

    @Bean
    ContractRegistry contractRegistry() {
        return new ContractRegistry();
    }

    @Bean
    ContractFactsPort claimsContractFactsPort(ContractRegistry registry) {
        return new ContractToClaimsAclAdapter(registry);
    }

    @Bean
    ClaimsEligibilityService claimsEligibilityService(ContractFactsPort facts) {
        return new ClaimsEligibilityService(facts);
    }
}
