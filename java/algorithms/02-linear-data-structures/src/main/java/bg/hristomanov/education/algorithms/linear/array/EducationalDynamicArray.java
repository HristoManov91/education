package bg.hristomanov.education.algorithms.linear.array;

import java.util.Arrays;

/**
 * Минимална dynamic-array implementation за учебни цели.
 *
 * <p>Целта е да се видят size/capacity, resize, copying и shifting разходите.
 * Не е replacement за {@link java.util.ArrayList}.</p>
 */
public final class EducationalDynamicArray<T> {

    private static final int DEFAULT_CAPACITY = 2;

    private Object[] elements;
    private int size;
    private long copiedElements;
    private long shiftedElements;
    private long indexedReads;

    public EducationalDynamicArray() {
        this(DEFAULT_CAPACITY);
    }

    public EducationalDynamicArray(int initialCapacity) {
        if (initialCapacity < 1) {
            throw new IllegalArgumentException("initialCapacity must be >= 1");
        }
        this.elements = new Object[initialCapacity];
    }

    public void add(T value) {
        ensureCapacity(size + 1);
        elements[size] = value;
        size++;
    }

    public void insert(int index, T value) {
        requireInsertIndex(index);
        ensureCapacity(size + 1);

        int elementsToMove = size - index;
        if (elementsToMove > 0) {
            System.arraycopy(elements, index, elements, index + 1, elementsToMove);
            shiftedElements += elementsToMove;
        }

        elements[index] = value;
        size++;
    }

    @SuppressWarnings("unchecked")
    public T get(int index) {
        requireElementIndex(index);
        indexedReads++;
        return (T) elements[index];
    }

    @SuppressWarnings("unchecked")
    public T remove(int index) {
        requireElementIndex(index);

        T removed = (T) elements[index];
        int elementsToMove = size - index - 1;
        if (elementsToMove > 0) {
            System.arraycopy(elements, index + 1, elements, index, elementsToMove);
            shiftedElements += elementsToMove;
        }

        size--;
        elements[size] = null;
        return removed;
    }

    private void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity <= elements.length) {
            return;
        }

        int newCapacity = Math.max(requiredCapacity, elements.length * 2);
        Object[] expanded = Arrays.copyOf(elements, newCapacity);
        copiedElements += size;
        elements = expanded;
    }

    private void requireElementIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
    }

    private void requireInsertIndex(int index) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return elements.length;
    }

    public long copiedElements() {
        return copiedElements;
    }

    public long shiftedElements() {
        return shiftedElements;
    }

    public long indexedReads() {
        return indexedReads;
    }

    public void resetMetrics() {
        copiedElements = 0;
        shiftedElements = 0;
        indexedReads = 0;
    }
}
