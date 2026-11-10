package bg.hristomanov.education.algorithms.hashing.model;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(new MapCopyLab.DefaultKey(1, 2).hashCode(),
                new MapCopyLab.DefaultKey(1, 2).hashCode());
    }

    @Test
    void invalidSizeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MapCopyLab.source(-1));
    }
}
