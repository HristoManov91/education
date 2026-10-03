package bg.hristomanov.education.algorithms.linear.deque;

/**
 * Array-based deque с circular indexing.
 *
 * <p>Класът демонстрира, че Stack / Queue / Deque са behavioral contracts
 * (поведенчески договори), а не задължително linked structures.</p>
 */
public final class CircularArrayDeque<T> {

    private static final int DEFAULT_CAPACITY = 4;

    private Object[] elements;
    private int head;
    private int size;

    public CircularArrayDeque() {
        this(DEFAULT_CAPACITY);
    }

    public CircularArrayDeque(int initialCapacity) {
        if (initialCapacity < 1) {
            throw new IllegalArgumentException("initialCapacity must be >= 1");
        }
        this.elements = new Object[initialCapacity];
    }

    public void addFirst(T value) {
        ensureCapacity(size + 1);
        head = decrement(head, elements.length);
        elements[head] = value;
        size++;
    }

    public void addLast(T value) {
        ensureCapacity(size + 1);
        int tailIndex = physicalIndex(size);
        elements[tailIndex] = value;
        size++;
    }

    @SuppressWarnings("unchecked")
    public T removeFirst() {
        requireNotEmpty();

        T value = (T) elements[head];
        elements[head] = null;
        head = increment(head, elements.length);
        size--;
        return value;
    }

    @SuppressWarnings("unchecked")
    public T removeLast() {
        requireNotEmpty();

        int lastIndex = physicalIndex(size - 1);
        T value = (T) elements[lastIndex];
        elements[lastIndex] = null;
        size--;
        return value;
    }

    @SuppressWarnings("unchecked")
    public T peekFirst() {
        requireNotEmpty();
        return (T) elements[head];
    }

    @SuppressWarnings("unchecked")
    public T peekLast() {
        requireNotEmpty();
        return (T) elements[physicalIndex(size - 1)];
    }

    private void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity <= elements.length) {
            return;
        }

        int newCapacity = elements.length * 2;
        Object[] expanded = new Object[newCapacity];

        for (int i = 0; i < size; i++) {
            expanded[i] = elements[physicalIndex(i)];
        }

        elements = expanded;
        head = 0;
    }

    private int physicalIndex(int logicalOffset) {
        return (head + logicalOffset) % elements.length;
    }

    private int increment(int index, int length) {
        return (index + 1) % length;
    }

    private int decrement(int index, int length) {
        return (index - 1 + length) % length;
    }

    private void requireNotEmpty() {
        if (size == 0) {
            throw new IllegalStateException("deque is empty");
        }
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return elements.length;
    }

    public boolean isEmpty() {
        return size == 0;
    }
}
