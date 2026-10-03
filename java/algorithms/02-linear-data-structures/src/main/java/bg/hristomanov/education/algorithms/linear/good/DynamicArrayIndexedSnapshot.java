package bg.hristomanov.education.algorithms.linear.good;

import bg.hristomanov.education.algorithms.linear.array.EducationalDynamicArray;
import bg.hristomanov.education.algorithms.linear.model.SnapshotResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Indexed snapshot върху array-based structure.
 *
 * <p>Всеки indexed read е директен достъп. За n елемента snapshot-ът прави
 * linear брой logical accesses.</p>
 */
public final class DynamicArrayIndexedSnapshot {

    public <T> SnapshotResult<T> snapshot(EducationalDynamicArray<T> source) {
        source.resetMetrics();
        List<T> values = new ArrayList<>(source.size());

        for (int i = 0; i < source.size(); i++) {
            values.add(source.get(i));
        }

        return new SnapshotResult<>(List.copyOf(values), source.indexedReads());
    }
}
