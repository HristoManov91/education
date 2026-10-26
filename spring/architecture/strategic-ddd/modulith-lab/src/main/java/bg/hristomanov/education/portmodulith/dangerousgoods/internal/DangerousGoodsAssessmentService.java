package bg.hristomanov.education.portmodulith.dangerousgoods.internal;

import bg.hristomanov.education.portmodulith.dangerousgoods.DangerousGoodsOperations;
import bg.hristomanov.education.portmodulith.dangerousgoods.events.DangerousGoodsAssessed;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class DangerousGoodsAssessmentService implements DangerousGoodsOperations {

    private final ApplicationEventPublisher events;

    public DangerousGoodsAssessmentService(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Override
    public DangerousGoodsAssessed assess(String vesselId, boolean documentationComplete) {
        DangerousGoodsAssessed result =
                new DangerousGoodsAssessed(vesselId, documentationComplete);
        events.publishEvent(result);
        return result;
    }
}
