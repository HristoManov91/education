package bg.hristomanov.education.algorithms.hashing.chaining;

import bg.hristomanov.education.algorithms.hashing.model.HashTableMetrics;

import java.util.Objects;

/**
 * Учебна hash table implementation със separate chaining.
 *
 * <p>Всеки bucket сочи към linked chain от entries. Collision (колизия) не е
 * грешка: два различни key-а могат законно да попаднат в един bucket.</p>
 */
public final class SeparateChainingHashTable<K, V> {

    private static final double MAX_LOAD_FACTOR = 0.75;

    private Object[] buckets;
    private int size;
    private long collisions;
    private long probes;
    private long rehashes;

    public SeparateChainingHashTable() {
        this(8);
    }

    public SeparateChainingHashTable(int initialCapacity) {
        if (initialCapacity < 2) {
            throw new IllegalArgumentException("initialCapacity must be >= 2");
        }
        this.buckets = new Object[initialCapacity];
    }

    public void put(K key, V value) {
        Objects.requireNonNull(key, "key");

        if ((size + 1.0) / buckets.length > MAX_LOAD_FACTOR) {
            resize(buckets.length * 2);
        }

        putInternal(key, value, true);
    }

    public V get(K key) {
        Objects.requireNonNull(key, "key");
        int bucketIndex = bucketIndex(key, buckets.length);

        Node<K, V> current = bucketAt(bucketIndex);
        while (current != null) {
            probes++;
            if (current.key.equals(key)) {
                return current.value;
            }
            current = current.next;
        }

        return null;
    }

    public V remove(K key) {
        Objects.requireNonNull(key, "key");
        int bucketIndex = bucketIndex(key, buckets.length);

        Node<K, V> previous = null;
        Node<K, V> current = bucketAt(bucketIndex);

        while (current != null) {
            probes++;
            if (current.key.equals(key)) {
                if (previous == null) {
                    buckets[bucketIndex] = current.next;
                } else {
                    previous.next = current.next;
                }
                size--;
                return current.value;
            }

            previous = current;
            current = current.next;
        }

        return null;
    }

    private void putInternal(K key, V value, boolean countMetrics) {
        int bucketIndex = bucketIndex(key, buckets.length);
        Node<K, V> current = bucketAt(bucketIndex);

        while (current != null) {
            if (countMetrics) {
                probes++;
            }
            if (current.key.equals(key)) {
                current.value = value;
                return;
            }
            current = current.next;
        }

        Node<K, V> head = bucketAt(bucketIndex);
        if (head != null && countMetrics) {
            collisions++;
        }

        buckets[bucketIndex] = new Node<>(key, value, head);
        size++;
    }

    private void resize(int newCapacity) {
        Object[] oldBuckets = buckets;
        buckets = new Object[newCapacity];
        int oldSize = size;
        size = 0;

        for (Object bucket : oldBuckets) {
            @SuppressWarnings("unchecked")
            Node<K, V> current = (Node<K, V>) bucket;

            while (current != null) {
                putInternal(current.key, current.value, false);
                current = current.next;
            }
        }

        if (size != oldSize) {
            throw new IllegalStateException("rehash changed table size");
        }

        rehashes++;
    }

    private int bucketIndex(K key, int capacity) {
        return Math.floorMod(spread(key.hashCode()), capacity);
    }

    private int spread(int hash) {
        return hash ^ (hash >>> 16);
    }

    @SuppressWarnings("unchecked")
    private Node<K, V> bucketAt(int index) {
        return (Node<K, V>) buckets[index];
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return buckets.length;
    }

    public double loadFactor() {
        return (double) size / buckets.length;
    }

    public HashTableMetrics metrics() {
        return new HashTableMetrics(collisions, probes, rehashes);
    }

    public void resetMetrics() {
        collisions = 0;
        probes = 0;
        rehashes = 0;
    }

    private static final class Node<K, V> {
        private final K key;
        private V value;
        private Node<K, V> next;

        private Node(K key, V value, Node<K, V> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }
}
