package bg.hristomanov.education.algorithms.complexity;

import bg.hristomanov.education.algorithms.complexity.demo.GrowingIntBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrowingIntBufferTest {

    @Test
    void expensiveResizesStillProduceLinearTotalCopyWorkAcrossManyAppends() {
        GrowingIntBuffer buffer = new GrowingIntBuffer();

        for (int i = 0; i < 1_024; i++) {
            buffer.add(i);
        }

        assertThat(buffer.size()).isEqualTo(1_024);
        assertThat(buffer.capacity()).isEqualTo(1_024);
        assertThat(buffer.copiedElements()).isEqualTo(1_023);
        assertThat(buffer.copiedElements()).isLessThan(buffer.size());
    }

    @Test
    void oneAppendCanTriggerLinearResizeWithoutChangingTheAmortizedStory() {
        GrowingIntBuffer buffer = new GrowingIntBuffer();

        for (int i = 0; i < 1_025; i++) {
            buffer.add(i);
        }

        assertThat(buffer.capacity()).isEqualTo(2_048);
        assertThat(buffer.copiedElements()).isEqualTo(2_047);
        assertThat((double) buffer.copiedElements() / buffer.size()).isLessThan(2.0);
    }
}
