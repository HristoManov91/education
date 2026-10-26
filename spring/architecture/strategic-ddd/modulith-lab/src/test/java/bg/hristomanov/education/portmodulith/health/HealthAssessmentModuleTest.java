package bg.hristomanov.education.portmodulith.health;

import static org.assertj.core.api.Assertions.assertThat;

import bg.hristomanov.education.portmodulith.clearance.OperationalClearanceQueries;
import bg.hristomanov.education.portmodulith.health.events.HealthAssessed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.PublishedEvents;

/**
 * Defaults to STANDALONE: boots only health and validates Spring wiring.
 * PublishedEvents captures the real synchronous Spring application event.
 */
@ApplicationModuleTest
class HealthAssessmentModuleTest {

    @Autowired
    HealthAssessmentOperations assessments;

    @Autowired
    ApplicationContext context;

    @Test
    void publishesAHealthAssessmentBusinessFact(PublishedEvents publishedEvents) {
        HealthAssessed result = assessments.assess("vessel-health-01", false);

        assertThat(result.accepted()).isTrue();
        assertThat(publishedEvents.ofType(HealthAssessed.class))
                .contains(result);
    }

    @Test
    void standaloneTestDoesNotBootstrapTheClearanceModule() {
        assertThat(context.getBeansOfType(OperationalClearanceQueries.class)).isEmpty();
    }

    @Test
    void healthRejectionHasAnExplicitBusinessOutcome(PublishedEvents publishedEvents) {
        HealthAssessed rejected = assessments.assess("vessel-health-rejected-01", true);

        assertThat(rejected.accepted()).isFalse();
        assertThat(publishedEvents.ofType(HealthAssessed.class)).contains(rejected);
    }
}
