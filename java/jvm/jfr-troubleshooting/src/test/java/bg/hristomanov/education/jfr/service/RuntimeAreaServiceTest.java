package bg.hristomanov.education.jfr.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class RuntimeAreaServiceTest {

    private final RuntimeAreaService service = new RuntimeAreaService();

    @Test
    void sameCoreWorkloadIsAvailableToHttpAndJmhContexts() {
        RuntimeAreaService.Result one = service.calculate(1, 5);
        RuntimeAreaService.Result three = service.calculate(3, 5);

        assertThat(one.evaluatedShapes()).isEqualTo(5);
        assertThat(three.evaluatedShapes()).isEqualTo(15);
        assertThat(one.doubledAreaSum()).isCloseTo(5 * 8 * Math.PI, offset(0.000000001));
        assertThat(three.doubledAreaSum()).isGreaterThan(one.doubledAreaSum());
    }

    @Test
    void boundedParametersAvoidUncontrolledHttpWorkloads() {
        assertThatThrownBy(() -> service.calculate(4, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculate(1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculate(1, 100_001))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
