package bg.hristomanov.education.algorithms.graphalgorithms.unionfind;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Disjoint Set Union / Union-Find с union-by-size и path compression.
 */
public final class UnionFind<T> {

    private final Map<T, T> parent = new LinkedHashMap<>();
    private final Map<T, Integer> componentSize = new LinkedHashMap<>();

    private long parentTraversals;
    private long pathCompressions;
    private long successfulUnions;

    public UnionFind(Collection<T> elements) {
        for (T element : elements) {
            Objects.requireNonNull(element, "Union-Find does not support null elements");

            if (parent.put(element, element) != null) {
                throw new IllegalArgumentException("duplicate element: " + element);
            }

            componentSize.put(element, 1);
        }
    }

    public T find(T element) {
        requireKnown(element);

        T current = element;
        while (!parent.get(current).equals(current)) {
            parentTraversals++;
            current = parent.get(current);
        }

        T root = current;
        current = element;

        while (!parent.get(current).equals(current)) {
            T next = parent.get(current);

            if (!parent.get(current).equals(root)) {
                parent.put(current, root);
                pathCompressions++;
            }

            current = next;
        }

        return root;
    }

    public boolean union(T first, T second) {
        T firstRoot = find(first);
        T secondRoot = find(second);

        if (firstRoot.equals(secondRoot)) {
            return false;
        }

        int firstSize = componentSize.get(firstRoot);
        int secondSize = componentSize.get(secondRoot);

        if (firstSize < secondSize) {
            T temporaryRoot = firstRoot;
            firstRoot = secondRoot;
            secondRoot = temporaryRoot;

            int temporarySize = firstSize;
            firstSize = secondSize;
            secondSize = temporarySize;
        }

        parent.put(secondRoot, firstRoot);
        componentSize.put(firstRoot, firstSize + secondSize);
        componentSize.remove(secondRoot);
        successfulUnions++;
        return true;
    }

    public boolean connected(T first, T second) {
        return find(first).equals(find(second));
    }

    public int componentSize(T element) {
        return componentSize.get(find(element));
    }

    public UnionFindMetrics metrics() {
        return new UnionFindMetrics(
                parentTraversals,
                pathCompressions,
                successfulUnions
        );
    }

    public void resetMetrics() {
        parentTraversals = 0;
        pathCompressions = 0;
        successfulUnions = 0;
    }

    private void requireKnown(T element) {
        if (!parent.containsKey(element)) {
            throw new IllegalArgumentException("unknown element: " + element);
        }
    }
}
