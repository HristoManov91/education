package bg.hristomanov.education.portmodulith.clearance;

import static org.assertj.core.api.Assertions.assertThat;

import bg.hristomanov.education.portmodulith.dangerousgoods.events.DangerousGoodsAssessed;
import bg.hristomanov.education.portmodulith.health.events.HealthAssessed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.test.ApplicationModuleTest;

/**
 * Sliced vertical module test: the producers are not part of this context.
 * Send their public event contracts into the consumer module.
 */
@ApplicationModuleTest
class OperationalClearanceModuleTest {

    @Autowired
    OperationalClearanceQueries clearances;

    @Autowired
    ApplicationEventPublisher events;

    @Test
    void clearanceNeedsBothPositiveAssessmentsInAnyOrder() {
        String vesselId = "vessel-clearance-01";
        assertThat(clearances.statusOf(vesselId)).isEqualTo(ClearanceStatus.PENDING);

        events.publishEvent(new DangerousGoodsAssessed(vesselId, true));
        assertThat(clearances.statusOf(vesselId)).isEqualTo(ClearanceStatus.PENDING);

        events.publishEvent(new HealthAssessed(vesselId, true));
        assertThat(clearances.statusOf(vesselId)).isEqualTo(ClearanceStatus.CLEARED);
    }

    @Test
    void aNegativeAssessmentDeniesClearanceWithoutWaitingForOtherModule() {
        String vesselId = "vessel-clearance-02";
        events.publishEvent(new HealthAssessed(vesselId, false));

        assertThat(clearances.statusOf(vesselId)).isEqualTo(ClearanceStatus.DENIED);
    }

    @Test
    void statusIsScopedToVesselIdentityRatherThanGlobalState() {
        events.publishEvent(new HealthAssessed("vessel-clearance-03", true));
        events.publishEvent(new DangerousGoodsAssessed("vessel-clearance-03", true));

        assertThat(clearances.statusOf("vessel-clearance-03"))
                .isEqualTo(ClearanceStatus.CLEARED);
        assertThat(clearances.statusOf("vessel-other"))
                .isEqualTo(ClearanceStatus.PENDING);
    }
}
