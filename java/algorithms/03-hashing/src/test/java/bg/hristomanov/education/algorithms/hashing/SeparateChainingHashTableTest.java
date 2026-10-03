package bg.hristomanov.education.algorithms.hashing;

import bg.hristomanov.education.algorithms.hashing.chaining.SeparateChainingHashTable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeparateChainingHashTableTest {

    @Test
    void differentKeysWithTheSameHashCanCoexistInOneBucketChain() {
        SeparateChainingHashTable<TestKey, String> table = new SeparateChainingHashTable<>(8);
        TestKey first = new TestKey("first", 7);
        TestKey second = new TestKey("second", 7);

        table.put(first, "A");
        table.put(second, "B");

        assertThat(table.get(first)).isEqualTo("A");
        assertThat(table.get(second)).isEqualTo("B");
        assertThat(table.size()).isEqualTo(2);
        assertThat(table.metrics().collisions()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void resizeRehashesEntriesWithoutLosingBusinessData() {
        SeparateChainingHashTable<Integer, String> table = new SeparateChainingHashTable<>(4);

        for (int i = 0; i < 20; i++) {
            table.put(i, "value-" + i);
        }

        assertThat(table.capacity()).isGreaterThan(4);
        assertThat(table.metrics().rehashes()).isGreaterThan(0);

        for (int i = 0; i < 20; i++) {
            assertThat(table.get(i)).isEqualTo("value-" + i);
        }
    }

    @Test
    void existingKeyIsUpdatedInsteadOfDuplicated() {
        SeparateChainingHashTable<TestKey, String> table = new SeparateChainingHashTable<>();
        TestKey key = new TestKey("same", 1);

        table.put(key, "old");
        table.put(key, "new");

        assertThat(table.size()).isEqualTo(1);
        assertThat(table.get(key)).isEqualTo("new");
    }
}
