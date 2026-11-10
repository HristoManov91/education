package bg.hristomanov.education.algorithms.hashing.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapCopyLabTest {

    @Test
    void constructorAndPutAllCopyPreserveEntriesForDifferentSourceViews() {
        HashMap<MapCopyLab.DefaultKey, Integer> original = MapCopyLab.source(150);
        for (Map<MapCopyLab.DefaultKey, Integer> source :
                List.of(original, MapCopyLab.unmodifiableView(original), Map.copyOf(original))) {
            assertEquals(source, MapCopyLab.constructorCopy(source));
            assertEquals(source, MapCopyLab.putAllCopy(source));
            assertNotSame(source, MapCopyLab.constructorCopy(source));
        }
    }

    @Test
    void copiedMapHasIndependentStructureButRecordKeysRetainValueEquality() {
        HashMap<MapCopyLab.DefaultKey, Integer> source = MapCopyLab.source(2);
        HashMap<MapCopyLab.DefaultKey, Integer> copy = MapCopyLab.constructorCopy(source);
        MapCopyLab.DefaultKey key = new MapCopyLab.DefaultKey(0, 0);
        assertEquals(source.get(key), copy.get(new MapCopyLab.DefaultKey(0, 0)));
        copy.remove(key);
        assertFalse(copy.containsKey(key));
        assertEquals(Integer.valueOf(0), source.get(key));
        MapCopyLab.DefaultKey first = new MapCopyLab.DefaultKey(1, 2);
        MapCopyLab.DefaultKey equal = new MapCopyLab.DefaultKey(1, 2);
        assertNotSame(first, equal);
        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        assertNotEquals(first, new MapCopyLab.DefaultKey(1, 3));

        MapCopyLab.ManualHashKey manuallyHashed = new MapCopyLab.ManualHashKey(1, 2);
        MapCopyLab.ManualHashKey manuallyHashedEqual = new MapCopyLab.ManualHashKey(1, 2);
        assertEquals(manuallyHashed, manuallyHashedEqual);
        assertEquals(manuallyHashed.hashCode(), manuallyHashedEqual.hashCode());
    }

    @Test
    void unmodifiableViewTracksItsSourceButSnapshotDoesNot() {
        HashMap<MapCopyLab.DefaultKey, Integer> source = MapCopyLab.source(1);
        Map<MapCopyLab.DefaultKey, Integer> view = MapCopyLab.unmodifiableView(source);
        HashMap<MapCopyLab.DefaultKey, Integer> snapshot = MapCopyLab.constructorCopy(view);

        MapCopyLab.DefaultKey newKey = new MapCopyLab.DefaultKey(1, 99);
        source.put(newKey, 42);
        assertEquals(Integer.valueOf(42), view.get(newKey));
        assertFalse(snapshot.containsKey(newKey));
        assertThrows(UnsupportedOperationException.class, () -> view.put(newKey, 55));
    }

    @Test
    void shallowCopySharesMutableValuesButNotMapEntries() {
        MapCopyLab.DefaultKey key = new MapCopyLab.DefaultKey(7, 8);
        List<String> tags = new ArrayList<>(List.of("initial"));
        HashMap<MapCopyLab.DefaultKey, List<String>> original = new HashMap<>();
        original.put(key, tags);

        HashMap<MapCopyLab.DefaultKey, List<String>> copy = new HashMap<>(original);
        tags.add("updated");
        assertEquals(List.of("initial", "updated"), copy.get(key));

        copy.remove(key);
        assertFalse(copy.containsKey(key));
        assertEquals(tags, original.get(key));
    }

    @Test
    void invalidSizeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MapCopyLab.source(-1));
    }
}
