package bg.hristomanov.education.algorithms.trees.heap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Generic binary min-heap.
 *
 * <p>Comparator-ът определя priority order-а. Най-малкият според comparator-а
 * element е root и може да бъде peek-нат за O(1) или poll-нат за O(log n).</p>
 *
 * <p>Heap-ът НЕ гарантира total ordering между arbitrary nodes. Затова
 * containsLinear(...) е O(n) и съществува тук нарочно като учебно доказателство.</p>
 */
public final class BinaryHeap<T> {

    private static final int DEFAULT_CAPACITY = 8;

    private final Comparator<? super T> comparator;
    private Object[] elements;
    private int size;

    private long comparisons;
    private long swaps;
    private long resizeCopies;
    private long linearSearchChecks;

    public BinaryHeap(Comparator<? super T> comparator) {
        this(comparator, DEFAULT_CAPACITY);
    }

    public BinaryHeap(Comparator<? super T> comparator, int initialCapacity) {
        this.comparator = Objects.requireNonNull(comparator, "comparator");

        if (initialCapacity < 1) {
            throw new IllegalArgumentException("initialCapacity must be >= 1");
        }

        this.elements = new Object[initialCapacity];
    }

    public void add(T value) {
        Objects.requireNonNull(value, "value");
        ensureCapacity(size + 1);

        elements[size] = value;
        siftUp(size);
        size++;
    }

    public T peek() {
        requireNotEmpty();
        return elementAt(0);
    }

    public T poll() {
        requireNotEmpty();

        T root = elementAt(0);
        int lastIndex = size - 1;
        T last = elementAt(lastIndex);

        elements[lastIndex] = null;
        size--;

        if (size > 0) {
            elements[0] = last;
            siftDown(0);
        }

        return root;
    }

    /**
     * Arbitrary lookup върху heap няма BST ordering guarantee, затова scan-ваме.
     */
    public boolean containsLinear(T value) {
        for (int i = 0; i < size; i++) {
            linearSearchChecks++;
            if (Objects.equals(elements[i], value)) {
                return true;
            }
        }

        return false;
    }

    public List<T> levelOrderArrayView() {
        List<T> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(elementAt(i));
        }
        return List.copyOf(values);
    }

    private void siftUp(int index) {
        int current = index;

        while (current > 0) {
            int parent = parentIndex(current);
            if (compare(current, parent) >= 0) {
                return;
            }

            swap(current, parent);
            current = parent;
        }
    }

    private void siftDown(int index) {
        int current = index;

        while (true) {
            int left = leftChildIndex(current);
            if (left >= size) {
                return;
            }

            int bestChild = left;
            int right = rightChildIndex(current);

            if (right < size && compare(right, left) < 0) {
                bestChild = right;
            }

            if (compare(bestChild, current) >= 0) {
                return;
            }

            swap(current, bestChild);
            current = bestChild;
        }
    }

    private int compare(int leftIndex, int rightIndex) {
        comparisons++;
        return comparator.compare(elementAt(leftIndex), elementAt(rightIndex));
    }

    private int parentIndex(int childIndex) {
        return (childIndex - 1) / 2;
    }

    private int leftChildIndex(int parentIndex) {
        return parentIndex * 2 + 1;
    }

    private int rightChildIndex(int parentIndex) {
        return parentIndex * 2 + 2;
    }

    private void swap(int leftIndex, int rightIndex) {
        Object temporary = elements[leftIndex];
        elements[leftIndex] = elements[rightIndex];
        elements[rightIndex] = temporary;
        swaps++;
    }

    private void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity <= elements.length) {
            return;
        }

        Object[] expanded = new Object[elements.length * 2];
        System.arraycopy(elements, 0, expanded, 0, size);
        resizeCopies += size;
        elements = expanded;
    }

    @SuppressWarnings("unchecked")
    private T elementAt(int index) {
        return (T) elements[index];
    }

    private void requireNotEmpty() {
        if (size == 0) {
            throw new IllegalStateException("heap is empty");
        }
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public HeapMetrics metrics() {
        return new HeapMetrics(comparisons, swaps, resizeCopies, linearSearchChecks);
    }

    public void resetMetrics() {
        comparisons = 0;
        swaps = 0;
        resizeCopies = 0;
        linearSearchChecks = 0;
    }
}
