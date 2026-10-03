package bg.hristomanov.education.algorithms.linear;

import bg.hristomanov.education.algorithms.linear.list.EducationalDoublyLinkedList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoublyLinkedListTest {

    @Test
    void removingAnAlreadyKnownNodeDoesNotTraverseTheList() {
        EducationalDoublyLinkedList<String> values = new EducationalDoublyLinkedList<>();
        values.addLast("A");
        EducationalDoublyLinkedList.Node<String> target = values.addLast("B");
        values.addLast("C");

        values.resetMetrics();
        String removed = values.remove(target);

        assertThat(removed).isEqualTo("B");
        assertThat(values.size()).isEqualTo(2);
        assertThat(values.traversalSteps()).isZero();
        assertThat(values.relinkOperations()).isEqualTo(2);
    }

    @Test
    void indexedAccessMustTraverseNodes() {
        EducationalDoublyLinkedList<Integer> values = new EducationalDoublyLinkedList<>();
        for (int i = 0; i < 20; i++) {
            values.addLast(i);
        }

        values.resetMetrics();
        Integer value = values.get(7);

        assertThat(value).isEqualTo(7);
        assertThat(values.traversalSteps()).isGreaterThan(0);
    }
}
