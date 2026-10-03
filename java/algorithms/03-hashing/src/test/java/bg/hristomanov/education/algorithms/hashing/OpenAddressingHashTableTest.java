package bg.hristomanov.education.algorithms.hashing;

import bg.hristomanov.education.algorithms.hashing.openaddressing.OpenAddressingHashTable;
import bg.hristomanov.education.algorithms.hashing.openaddressing.ProbeStrategy;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAddressingHashTableTest {

    @ParameterizedTest
    @EnumSource(ProbeStrategy.class)
    void everyProbeStrategyKeepsCollidingKeysReachable(ProbeStrategy strategy) {
        OpenAddressingHashTable<TestKey, String> table = new OpenAddressingHashTable<>(strategy, 11);

        TestKey first = new TestKey("first", 5);
        TestKey second = new TestKey("second", 5);
        TestKey third = new TestKey("third", 5);

        table.put(first, "A");
        table.put(second, "B");
        table.put(third, "C");

        assertThat(table.get(first)).isEqualTo("A");
        assertThat(table.get(second)).isEqualTo("B");
        assertThat(table.get(third)).isEqualTo("C");
        assertThat(table.metrics().collisions()).isGreaterThan(0);
    }

    @Test
    void tombstonePreservesTheProbeChainAfterDeletion() {
        OpenAddressingHashTable<TestKey, String> table =
                new OpenAddressingHashTable<>(ProbeStrategy.LINEAR, 11);

        TestKey first = new TestKey("first", 3);
        TestKey second = new TestKey("second", 3);
        TestKey third = new TestKey("third", 3);

        table.put(first, "A");
        table.put(second, "B");
        table.put(third, "C");

        assertThat(table.remove(first)).isEqualTo("A");

        // Ако first slot беше станал EMPTY, linear lookup би спрял твърде рано.
        assertThat(table.get(second)).isEqualTo("B");
        assertThat(table.get(third)).isEqualTo("C");
    }

    @Test
    void tableResizesBeforeHighLoadDestroysProbePerformance() {
        OpenAddressingHashTable<Integer, Integer> table =
                new OpenAddressingHashTable<>(ProbeStrategy.LINEAR, 5);

        int initialCapacity = table.capacity();

        table.put(1, 1);
        table.put(2, 2);
        table.put(3, 3);

        assertThat(table.capacity()).isGreaterThan(initialCapacity);
        assertThat(table.metrics().rehashes()).isGreaterThan(0);
        assertThat(table.get(1)).isEqualTo(1);
        assertThat(table.get(2)).isEqualTo(2);
        assertThat(table.get(3)).isEqualTo(3);
    }
}
