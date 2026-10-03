package bg.hristomanov.education.algorithms.linear;

import bg.hristomanov.education.algorithms.linear.array.EducationalDynamicArray;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicArrayTest {

    @Test
    void growsByCopyingWhenCapacityIsExhausted() {
        EducationalDynamicArray<Integer> values = new EducationalDynamicArray<>(2);

        values.add(10);
        values.add(20);
        values.add(30);

        assertThat(values.size()).isEqualTo(3);
        assertThat(values.capacity()).isEqualTo(4);
        assertThat(values.copiedElements()).isEqualTo(2);
        assertThat(values.get(0)).isEqualTo(10);
        assertThat(values.get(2)).isEqualTo(30);
    }

    @Test
    void middleInsertAndRemoveRequireShifting() {
        EducationalDynamicArray<String> values = new EducationalDynamicArray<>(4);
        values.add("A");
        values.add("C");

        values.resetMetrics();
        values.insert(1, "B");

        assertThat(values.get(0)).isEqualTo("A");
        assertThat(values.get(1)).isEqualTo("B");
        assertThat(values.get(2)).isEqualTo("C");
        assertThat(values.shiftedElements()).isEqualTo(1);

        values.resetMetrics();
        String removed = values.remove(0);

        assertThat(removed).isEqualTo("A");
        assertThat(values.shiftedElements()).isEqualTo(2);
    }
}
