package bg.hristomanov.education.portmodulith;

import static org.assertj.core.api.Assertions.assertThat;

import bg.hristomanov.education.portmodulith.clearance.ClearanceStatus;
import bg.hristomanov.education.portmodulith.clearance.OperationalClearanceQueries;
import bg.hristomanov.education.portmodulith.dangerousgoods.DangerousGoodsOperations;
import bg.hristomanov.education.portmodulith.health.HealthAssessmentOperations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * One deployable full-context test: both producers publish real in-process
 * Spring ApplicationEvents and the owning clearance module consumes them.
 */
@SpringBootTest(classes = HarbourModulithApplication.class)
class PortClearanceEndToEndSpringTest {

    @Autowired
    HealthAssessmentOperations health;

    @Autowired
    DangerousGoodsOperations goods;

    @Autowired
    OperationalClearanceQueries clearance;

    @Test
    void eventDrivenClearanceIsDerivedFromBothModulesWithoutDirectServiceCalls() {
        String id = "vessel-full-context-01";
        assertThat(clearance.statusOf(id)).isEqualTo(ClearanceStatus.PENDING);

        health.assess(id, false);
        assertThat(clearance.statusOf(id)).isEqualTo(ClearanceStatus.PENDING);

        goods.assess(id, true);
        assertThat(clearance.statusOf(id)).isEqualTo(ClearanceStatus.CLEARED);
    }

    @Test
    void rejectedDangerousGoodsAssessmentBlocksClearance() {
        String id = "vessel-full-context-02";

        health.assess(id, false);
        goods.assess(id, false);

        assertThat(clearance.statusOf(id)).isEqualTo(ClearanceStatus.DENIED);
    }
}
