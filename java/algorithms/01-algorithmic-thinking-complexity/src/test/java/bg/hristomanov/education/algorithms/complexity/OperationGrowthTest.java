package bg.hristomanov.education.algorithms.complexity;

import bg.hristomanov.education.algorithms.complexity.demo.OperationGrowth;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationGrowthTest {

    @Test
    void linearWorkGrowsTenTimesWhenInputGrowsTenTimes() {
        assertThat(OperationGrowth.linearSteps(100)).isEqualTo(100);
        assertThat(OperationGrowth.linearSteps(1_000)).isEqualTo(1_000);
    }

    @Test
    void quadraticWorkGrowsOneHundredTimesWhenInputGrowsTenTimes() {
        long small = OperationGrowth.quadraticSteps(100);
        long large = OperationGrowth.quadraticSteps(1_000);

        assertThat(small).isEqualTo(10_000);
        assertThat(large).isEqualTo(1_000_000);
        assertThat(large / small).isEqualTo(100);
    }

    @Test
    void halvingWorkAddsOnlyTenStepsWhenInputGrowsByFactorOf1024() {
        assertThat(OperationGrowth.halvingSteps(1_024)).isEqualTo(10);
        assertThat(OperationGrowth.halvingSteps(1_048_576)).isEqualTo(20);
    }
}
