package bg.hristomanov.education.sequenced;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.*;

class CollectionOrdersTest {

    @Test
    void reversedReturnsBackedViewAndMutatingItChangesSource() {
        List<String> original = new ArrayList<>(List.of("A", "B", "C"));
        var reversed = CollectionOrders.reversedBackedView(original);
        assertThat(reversed.getFirst()).isEqualTo("C");

        reversed.removeFirst();
        assertThat(original).containsExactly("A", "B");

        original.add("D");
        assertThat(reversed.getFirst()).isEqualTo("D");
    }

    @Test
    void defensiveCopyFromReversedViewIsNotBacked() {
        List<String> original = new ArrayList<>(List.of("old", "new"));
        List<String> snapshot = CollectionOrders.newestFirst(original);
        original.add("newest");
        assertThat(snapshot).containsExactly("new", "old");
    }

    @Test
    void linkedSequencedSetCanMoveExistingKeyToFront() {
        var accessOrder = CollectionOrders.maintainAccessOrder(
                List.of("A", "B", "C", "A"));
        assertThat(accessOrder).containsExactly("A", "C", "B");
    }

    @Test
    void sequencedMapHasExplicitFirstAndLastEntries() {
        var map = CollectionOrders.priorityMap();
        assertThat(map.firstEntry().getKey()).isEqualTo(1);
        assertThat(map.lastEntry().getKey()).isEqualTo(3);
        assertThat(map.sequencedKeySet()).containsExactly(1, 2, 3);
    }

    @Test
    void sortedSetCannotSatisfyExplicitInsertionOrderMethods() {
        var sorted = new TreeSet<>(List.of("B", "A"));
        assertThatThrownBy(() -> sorted.addFirst("C"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> sorted.addLast("C"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(sorted).containsExactly("A", "B");
    }
}
