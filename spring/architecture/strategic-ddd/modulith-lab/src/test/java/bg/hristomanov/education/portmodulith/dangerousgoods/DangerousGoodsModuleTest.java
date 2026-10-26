package bg.hristomanov.education.portmodulith.dangerousgoods;

import static org.assertj.core.api.Assertions.assertThat;

import bg.hristomanov.education.portmodulith.dangerousgoods.events.DangerousGoodsAssessed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.PublishedEvents;

@ApplicationModuleTest
class DangerousGoodsModuleTest {

    @Autowired
    DangerousGoodsOperations assessments;

    @Test
    void publishesBothPositiveAndNegativeBusinessOutcomes(PublishedEvents events) {
        DangerousGoodsAssessed accepted = assessments.assess("vessel-goods-01", true);
        DangerousGoodsAssessed rejected = assessments.assess("vessel-goods-02", false);

        assertThat(events.ofType(DangerousGoodsAssessed.class))
                .contains(accepted, rejected);
        assertThat(accepted.accepted()).isTrue();
        assertThat(rejected.accepted()).isFalse();
    }
}
