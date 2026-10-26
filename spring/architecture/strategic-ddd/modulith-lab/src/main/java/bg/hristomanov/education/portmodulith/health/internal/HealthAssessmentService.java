package bg.hristomanov.education.portmodulith.health.internal;

import bg.hristomanov.education.portmodulith.health.HealthAssessmentOperations;
import bg.hristomanov.education.portmodulith.health.events.HealthAssessed;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Internal implementation: other modules must not import this class.
 * Business semantics are intentionally simplified for the architecture lab.
 */
@Service
public class HealthAssessmentService implements HealthAssessmentOperations {

    private final ApplicationEventPublisher events;

    public HealthAssessmentService(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Override
    public HealthAssessed assess(String vesselId, boolean infectiousConditionReported) {
        HealthAssessed result = new HealthAssessed(vesselId, !infectiousConditionReported);
        events.publishEvent(result);
        return result;
    }
}
