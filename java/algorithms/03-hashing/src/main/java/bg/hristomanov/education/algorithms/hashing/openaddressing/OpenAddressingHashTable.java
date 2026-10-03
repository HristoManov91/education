package bg.hristomanov.education.algorithms.hashing.openaddressing;

import bg.hristomanov.education.algorithms.hashing.model.HashTableMetrics;

import java.util.Objects;

/**
 * Учебна open-addressing hash table.
 *
 * <p>Всички entries живеят директно в table array-а. При collision търсим
 * следваща позиция чрез избраната {@link ProbeStrategy}. Deleted slot не става
 * EMPTY, а TOMBSTONE (маркер за изтрита позиция), за да не прекъснем probe chain-а.</p>
 */
public final class OpenAddressingHashTable<K, V> {

    private static final byte EMPTY = 0;
    private static final byte OCCUPIED = 1;
    private static final byte TOMBSTONE = 2;

    /**
     * При quadratic probing и prime capacity държим таблицата под 50% load,
     * за да имаме достатъчно probe candidates и предвидимо учебно поведение.
     */
    private static final double MAX_LOAD_FACTOR = 0.50;

    private final ProbeStrategy probeStrategy;

    private Object[] keys;
    private Object[] values;
    private byte[] states;
    private int size;

    private long collisions;
    private long probes;
    private long rehashes;

    public OpenAddressingHashTable(ProbeStrategy probeStrategy) {
        this(probeStrategy, 11);
    }

    public OpenAddressingHashTable(ProbeStrategy probeStrategy, int initialCapacity) {
        this.probeStrategy = Objects.requireNonNull(probeStrategy, "probeStrategy");

        int capacity = nextPrime(Math.max(3, initialCapacity));
        keys = new Object[capacity];
        values = new Object[capacity];
        states = new byte[capacity];
    }

    public void put(K key, V value) {
        Objects.requireNonNull(key, "key");

        if ((size + 1.0) / keys.length > MAX_LOAD_FACTOR) {
            resize(nextPrime(keys.length * 2));
        }

        putInternal(key, value, true);
    }

    public V get(K key) {
        Objects.requireNonNull(key, "key");

        int keyHash = spread(key.hashCode());
        int baseIndex = Math.floorMod(keyHash, keys.length);

        for (int attempt = 0; attempt < keys.length; attempt++) {
            int index = probeStrategy.index(baseIndex, keyHash, attempt, keys.length);
            probes++;

            if (states[index] == EMPTY) {
                return null;
            }

            if (states[index] == OCCUPIED && keyAt(index).equals(key)) {
                return valueAt(index);
            }
        }

        return null;
    }

    public V remove(K key) {
        Objects.requireNonNull(key, "key");

        int keyHash = spread(key.hashCode());
        int baseIndex = Math.floorMod(keyHash, keys.length);

        for (int attempt = 0; attempt < keys.length; attempt++) {
            int index = probeStrategy.index(baseIndex, keyHash, attempt, keys.length);
            probes++;

            if (states[index] == EMPTY) {
                return null;
            }

            if (states[index] == OCCUPIED && keyAt(index).equals(key)) {
                V removed = valueAt(index);

                keys[index] = null;
                values[index] = null;
                states[index] = TOMBSTONE;
                size--;

                return removed;
            }
        }

        return null;
    }

    private void putInternal(K key, V value, boolean countMetrics) {
        int keyHash = spread(key.hashCode());
        int baseIndex = Math.floorMod(keyHash, keys.length);
        int firstTombstone = -1;

        for (int attempt = 0; attempt < keys.length; attempt++) {
            int index = probeStrategy.index(baseIndex, keyHash, attempt, keys.length);

            if (countMetrics) {
                probes++;
            }

            if (states[index] == OCCUPIED) {
                if (keyAt(index).equals(key)) {
                    values[index] = value;
                    return;
                }

                if (countMetrics) {
                    collisions++;
                }
                continue;
            }

            if (states[index] == TOMBSTONE) {
                if (firstTombstone < 0) {
                    firstTombstone = index;
                }
                continue;
            }

            int targetIndex = firstTombstone >= 0 ? firstTombstone : index;
            writeEntry(targetIndex, key, value);
            return;
        }

        if (firstTombstone >= 0) {
            writeEntry(firstTombstone, key, value);
            return;
        }

        resize(nextPrime(keys.length * 2));
        putInternal(key, value, countMetrics);
    }

    private void writeEntry(int index, K key, V value) {
        keys[index] = key;
        values[index] = value;
        states[index] = OCCUPIED;
        size++;
    }

    private void resize(int newCapacity) {
        Object[] oldKeys = keys;
        Object[] oldValues = values;
        byte[] oldStates = states;
        int oldSize = size;

        keys = new Object[newCapacity];
        values = new Object[newCapacity];
        states = new byte[newCapacity];
        size = 0;

        for (int i = 0; i < oldKeys.length; i++) {
            if (oldStates[i] == OCCUPIED) {
                @SuppressWarnings("unchecked")
                K key = (K) oldKeys[i];
                @SuppressWarnings("unchecked")
                V value = (V) oldValues[i];

                putInternal(key, value, false);
            }
        }

        if (size != oldSize) {
            throw new IllegalStateException("rehash changed table size");
        }

        rehashes++;
    }

    private int spread(int hash) {
        return hash ^ (hash >>> 16);
    }

    private int nextPrime(int candidate) {
        int value = candidate;
        while (!isPrime(value)) {
            value++;
        }
        return value;
    }

    private boolean isPrime(int value) {
        if (value < 2) {
            return false;
        }
        if (value % 2 == 0) {
            return value == 2;
        }

        for (int divisor = 3; (long) divisor * divisor <= value; divisor += 2) {
            if (value % divisor == 0) {
                return false;
            }
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private K keyAt(int index) {
        return (K) keys[index];
    }

    @SuppressWarnings("unchecked")
    private V valueAt(int index) {
        return (V) values[index];
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return keys.length;
    }

    public double loadFactor() {
        return (double) size / keys.length;
    }

    public HashTableMetrics metrics() {
        return new HashTableMetrics(collisions, probes, rehashes);
    }

    public void resetMetrics() {
        collisions = 0;
        probes = 0;
        rehashes = 0;
    }
}
