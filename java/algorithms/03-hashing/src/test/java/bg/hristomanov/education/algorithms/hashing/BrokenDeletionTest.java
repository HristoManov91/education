package bg.hristomanov.education.algorithms.hashing;

import bg.hristomanov.education.algorithms.hashing.bad.BrokenLinearProbingHashTable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrokenDeletionTest {

    @Test
    void clearingASlotToEmptyBreaksLookupForLaterCollidingEntries() {
        BrokenLinearProbingHashTable<TestKey, String> table =
                new BrokenLinearProbingHashTable<>(11);

        TestKey first = new TestKey("first", 4);
        TestKey second = new TestKey("second", 4);

        table.put(first, "A");
        table.put(second, "B");

        assertThat(table.get(second)).isEqualTo("B");

        table.remove(first);

        // Тестът доказва broken semantics на naive deletion.
        assertThat(table.get(second)).isNull();
    }
}
