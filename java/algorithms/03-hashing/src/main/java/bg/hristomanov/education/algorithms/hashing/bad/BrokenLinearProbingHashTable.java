package bg.hristomanov.education.algorithms.hashing.bad;

import java.util.Objects;

/**
 * Нарочно broken open-addressing implementation.
 *
 * <p>Грешката е в remove(): slot-ът става директно EMPTY. Това прекъсва probe
 * chain-а и може да направи по-късно вмъкнат collided key невидим за get().</p>
 */
public final class BrokenLinearProbingHashTable<K, V> {

    private final Object[] keys;
    private final Object[] values;
    private final boolean[] occupied;

    public BrokenLinearProbingHashTable(int capacity) {
        if (capacity < 3) {
            throw new IllegalArgumentException("capacity must be >= 3");
        }
        keys = new Object[capacity];
        values = new Object[capacity];
        occupied = new boolean[capacity];
    }

    public void put(K key, V value) {
        Objects.requireNonNull(key, "key");
        int start = Math.floorMod(key.hashCode(), keys.length);

        for (int attempt = 0; attempt < keys.length; attempt++) {
            int index = (start + attempt) % keys.length;

            if (!occupied[index] || keyAt(index).equals(key)) {
                keys[index] = key;
                values[index] = value;
                occupied[index] = true;
                return;
            }
        }

        throw new IllegalStateException("table is full");
    }

    public V get(K key) {
        Objects.requireNonNull(key, "key");
        int start = Math.floorMod(key.hashCode(), keys.length);

        for (int attempt = 0; attempt < keys.length; attempt++) {
            int index = (start + attempt) % keys.length;

            if (!occupied[index]) {
                return null;
            }

            if (keyAt(index).equals(key)) {
                return valueAt(index);
            }
        }

        return null;
    }

    public V remove(K key) {
        Objects.requireNonNull(key, "key");
        int start = Math.floorMod(key.hashCode(), keys.length);

        for (int attempt = 0; attempt < keys.length; attempt++) {
            int index = (start + attempt) % keys.length;

            if (!occupied[index]) {
                return null;
            }

            if (keyAt(index).equals(key)) {
                V removed = valueAt(index);

                // BUG: EMPTY прекъсва probe chain-а. Правилният open-addressing
                // подход използва tombstone/deleted marker.
                occupied[index] = false;
                keys[index] = null;
                values[index] = null;
                return removed;
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private K keyAt(int index) {
        return (K) keys[index];
    }

    @SuppressWarnings("unchecked")
    private V valueAt(int index) {
        return (V) values[index];
    }
}
