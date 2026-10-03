package bg.hristomanov.education.algorithms.linear;

import bg.hristomanov.education.algorithms.linear.array.EducationalDynamicArray;
import bg.hristomanov.education.algorithms.linear.deque.CircularArrayDeque;
import bg.hristomanov.education.algorithms.linear.bad.LinkedListIndexedSnapshot;
import bg.hristomanov.education.algorithms.linear.good.DynamicArrayIndexedSnapshot;
import bg.hristomanov.education.algorithms.linear.list.EducationalDoublyLinkedList;
import bg.hristomanov.education.algorithms.linear.model.SnapshotResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IndexedSnapshotTradeOffTest {

    private final LinkedListIndexedSnapshot badSnapshot = new LinkedListIndexedSnapshot();
    private final DynamicArrayIndexedSnapshot goodSnapshot = new DynamicArrayIndexedSnapshot();

    @Test
    void sameSnapshotSemanticsHaveVeryDifferentAccessShapes() {
        int size = 1_000;
        EducationalDynamicArray<Integer> array = new EducationalDynamicArray<>();
        EducationalDoublyLinkedList<Integer> linkedList = new EducationalDoublyLinkedList<>();

        for (int i = 0; i < size; i++) {
            array.add(i);
            linkedList.addLast(i);
        }

        SnapshotResult<Integer> arrayResult = goodSnapshot.snapshot(array);
        SnapshotResult<Integer> linkedResult = badSnapshot.snapshot(linkedList);

        assertThat(linkedResult.values()).isEqualTo(arrayResult.values());
        assertThat(arrayResult.logicalAccessSteps()).isEqualTo(size);
        assertThat(linkedResult.logicalAccessSteps()).isGreaterThan(200_000);
    }

    @Test
    void dequeBehaviorIsAboutAccessOrderNotAboutOneSpecificImplementation() {
        CircularArrayDeque<Integer> deque = new CircularArrayDeque<>();
        deque.addLast(10);
        deque.addLast(20);
        deque.addFirst(5);

        List<Integer> observed = List.of(
                deque.removeFirst(),
                deque.removeFirst(),
                deque.removeFirst()
        );

        assertThat(observed).containsExactly(5, 10, 20);
    }
}
