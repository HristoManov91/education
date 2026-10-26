package bg.hristomanov.education.sequenced;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedCollection;
import java.util.SequencedMap;
import java.util.SequencedSet;

/**
 * Since Java 21 these interfaces express an encounter-order contract instead
 * of forcing callers to depend on List indexes or concrete implementations.
 */
public final class CollectionOrders {

    private CollectionOrders() {
    }

    public static <T> List<T> newestFirst(SequencedCollection<T> chronological) {
        return List.copyOf(chronological.reversed());
    }

    public static SequencedSet<String> maintainAccessOrder(Iterable<String> incoming) {
        SequencedSet<String> entries = new LinkedHashSet<>();
        for (String entry : incoming) {
            // For an existing key addFirst moves it to the front.
            entries.addFirst(entry);
        }
        return entries;
    }

    public static SequencedMap<Integer, String> priorityMap() {
        SequencedMap<Integer, String> steps = new LinkedHashMap<>();
        steps.putLast(2, "normal");
        steps.putFirst(1, "urgent");
        steps.putLast(3, "later");
        return steps;
    }

    /** The returned reversed list is a backed view, NOT a defensive copy. */
    public static SequencedCollection<String> reversedBackedView(List<String> original) {
        return original.reversed();
    }
}
