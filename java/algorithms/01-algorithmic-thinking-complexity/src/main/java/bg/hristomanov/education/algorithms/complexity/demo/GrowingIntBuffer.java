package bg.hristomanov.education.algorithms.complexity.demo;

/**
 * Минимална dynamic-array-like структура само за демонстрация на amortized cost.
 *
 * <p>Не е replacement за ArrayList. Целта е да се види, че отделният resize е
 * O(n), но при doubling strategy общият брой копирани елементи за много append
 * операции остава линеен спрямо броя добавяния.</p>
 */
public final class GrowingIntBuffer {

    private int[] elements = new int[1];
    private int size;
    private long copiedElements;

    public void add(int value) {
        if (size == elements.length) {
            grow();
        }

        elements[size] = value;
        size++;
    }

    private void grow() {
        int[] expanded = new int[elements.length * 2];
        System.arraycopy(elements, 0, expanded, 0, size);
        copiedElements += size;
        elements = expanded;
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
}
