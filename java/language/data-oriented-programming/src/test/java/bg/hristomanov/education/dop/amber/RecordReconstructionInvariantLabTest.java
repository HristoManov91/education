package bg.hristomanov.education.dop.amber;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bg.hristomanov.education.dop.amber.RecordReconstructionInvariantLab.ItemizedTaskBatch;
import bg.hristomanov.education.dop.amber.RecordReconstructionInvariantLab.ParallelTaskBatch;
import bg.hristomanov.education.dop.amber.RecordReconstructionInvariantLab.TaskPriority;

class RecordReconstructionInvariantLabTest {

    @Test
    void replacingBothListsTogetherPreservesConstructorInvariantAndOriginal() {
        ParallelTaskBatch original =
                new ParallelTaskBatch(List.of("A", "B"), List.of(3, 4));

        assertThrows(IllegalArgumentException.class,
                () -> original.replaceOnlyTaskIdsForContrast(List.of("A", "B", "C")));

        ParallelTaskBatch updated = original.replaceTogether(
                List.of("A", "B", "C"), List.of(3, 4, 5));

        assertEquals(List.of("A", "B", "C"), updated.taskIds());
        assertEquals(List.of(3, 4, 5), updated.priorities());
        assertEquals(List.of("A", "B"), original.taskIds());
        assertEquals(List.of(3, 4), original.priorities());
        assertNotSame(original, updated);
    }

    @Test
    void mismatchedNullDuplicateAndInvalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ParallelTaskBatch(List.of("A"), List.of(1, 2)));
        assertThrows(IllegalArgumentException.class,
                () -> new ParallelTaskBatch(List.of("A", "A"), List.of(1, 2)));
        assertThrows(IllegalArgumentException.class,
                () -> new ParallelTaskBatch(List.of(""), List.of(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ParallelTaskBatch(List.of("A"), List.of(0)));
        assertThrows(IllegalArgumentException.class,
                () -> new ParallelTaskBatch(List.of("A"), List.of(11)));
        assertThrows(NullPointerException.class,
                () -> new ParallelTaskBatch(null, List.of(1)));
        assertThrows(NullPointerException.class,
                () -> new ParallelTaskBatch(List.of("A"), null));
    }

    @Test
    void defensiveCopiesProtectAgainstExternalMutationAndNullElements() {
        ArrayList<String> ids = new ArrayList<>(List.of("A"));
        ArrayList<Integer> priorities = new ArrayList<>(List.of(2));
        ParallelTaskBatch batch = new ParallelTaskBatch(ids, priorities);
        ids.add("B");
        priorities.set(0, 7);
        assertEquals(List.of("A"), batch.taskIds());
        assertEquals(List.of(2), batch.priorities());
        assertThrows(UnsupportedOperationException.class, () -> batch.taskIds().add("C"));
        assertThrows(NullPointerException.class,
                () -> new ParallelTaskBatch(java.util.Arrays.asList((String) null), List.of(1)));
    }

    @Test
    void normalizedTaskPriorityStructureMakesParallelLengthsImpossible() {
        ParallelTaskBatch original = new ParallelTaskBatch(
                List.of("A", "B"), List.of(3, 4));
        ItemizedTaskBatch normalized = RecordReconstructionInvariantLab.normalize(original);
        assertEquals(List.of(new TaskPriority("A", 3), new TaskPriority("B", 4)),
                normalized.tasks());
        ItemizedTaskBatch revised = normalized.changePriority("B", 9);
        assertEquals(new TaskPriority("B", 9), revised.tasks().get(1));
        assertEquals(new TaskPriority("B", 4), normalized.tasks().get(1));
        assertThrows(IllegalArgumentException.class,
                () -> normalized.changePriority("MISSING", 6));
    }

    @Test
    void itemizedModelStillEnforcesIdentityAndValueConstraints() {
        assertThrows(IllegalArgumentException.class,
                () -> new ItemizedTaskBatch(List.of(
                        new TaskPriority("A", 1), new TaskPriority("A", 2))));
        assertThrows(IllegalArgumentException.class,
                () -> new TaskPriority("A", -1));
        assertThrows(IllegalArgumentException.class,
                () -> new TaskPriority(" ", 3));
        ItemizedTaskBatch empty = new ItemizedTaskBatch(List.of());
        assertTrue(empty.tasks().isEmpty());
    }
}
