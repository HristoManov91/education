package bg.hristomanov.education.algorithms.trees;

import bg.hristomanov.education.algorithms.trees.heap.BinaryHeap;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

class BinaryHeapTest {

    @Test
    void minHeapKeepsSmallestElementAtTheRoot() {
        BinaryHeap<Integer> heap = new BinaryHeap<>(Comparator.naturalOrder());

        heap.add(9);
        heap.add(2);
        heap.add(7);
        heap.add(1);
        heap.add(5);
        heap.add(3);

        assertThat(heap.peek()).isEqualTo(1);
        assertThat(heap.poll()).isEqualTo(1);
        assertThat(heap.poll()).isEqualTo(2);
        assertThat(heap.poll()).isEqualTo(3);
        assertThat(heap.poll()).isEqualTo(5);
        assertThat(heap.poll()).isEqualTo(7);
        assertThat(heap.poll()).isEqualTo(9);
    }

    @Test
    void maxHeapIsTheSameStructureWithAReversedPriorityOrder() {
        BinaryHeap<Integer> heap = new BinaryHeap<>(Comparator.reverseOrder());

        heap.add(4);
        heap.add(10);
        heap.add(7);

        assertThat(heap.peek()).isEqualTo(10);
        assertThat(heap.poll()).isEqualTo(10);
        assertThat(heap.poll()).isEqualTo(7);
        assertThat(heap.poll()).isEqualTo(4);
    }

    @Test
    void arbitraryLookupStillRequiresLinearScanning() {
        BinaryHeap<Integer> heap = new BinaryHeap<>(Comparator.naturalOrder());

        for (int value = 1; value <= 1_000; value++) {
            heap.add(value);
        }

        heap.resetMetrics();

        assertThat(heap.containsLinear(2_000)).isFalse();
        assertThat(heap.metrics().linearSearchChecks()).isEqualTo(1_000);
    }
}
