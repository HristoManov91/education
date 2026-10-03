package bg.hristomanov.education.algorithms.linear.bad;

import bg.hristomanov.education.algorithms.linear.list.EducationalDoublyLinkedList;
import bg.hristomanov.education.algorithms.linear.model.SnapshotResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Реалистичен anti-pattern: linked list се третира като random-access structure.
 *
 * <p>Всеки get(index) изисква traversal до node. Повтарянето му за всички
 * индекси превръща иначе прост snapshot в quadratic-shaped work.</p>
 */
public final class LinkedListIndexedSnapshot {

    public <T> SnapshotResult<T> snapshot(EducationalDoublyLinkedList<T> source) {
        source.resetMetrics();
        List<T> values = new ArrayList<>(source.size());

        for (int i = 0; i < source.size(); i++) {
            values.add(source.get(i));
        }

        long logicalSteps = source.size() + source.traversalSteps();
        return new SnapshotResult<>(List.copyOf(values), logicalSteps);
    }
}
