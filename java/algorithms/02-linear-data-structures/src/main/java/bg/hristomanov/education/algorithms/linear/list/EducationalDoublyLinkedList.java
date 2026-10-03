package bg.hristomanov.education.algorithms.linear.list;

/**
 * Минимална doubly linked list implementation за учебни цели.
 *
 * <p>Особено важна е разликата между:</p>
 * <ul>
 *     <li>имаме reference към node → unlink може да е O(1);</li>
 *     <li>имаме само index/value → първо трябва да стигнем до node чрез traversal.</li>
 * </ul>
 */
public final class EducationalDoublyLinkedList<T> {

    public static final class Node<T> {
        private final EducationalDoublyLinkedList<T> owner;
        private final T value;
        private Node<T> previous;
        private Node<T> next;
        private boolean linked = true;

        private Node(EducationalDoublyLinkedList<T> owner, T value) {
            this.owner = owner;
            this.value = value;
        }

        public T value() {
            return value;
        }
    }

    private Node<T> head;
    private Node<T> tail;
    private int size;
    private long traversalSteps;
    private long relinkOperations;

    public Node<T> addFirst(T value) {
        Node<T> node = new Node<>(this, value);
        Node<T> oldHead = head;

        node.next = oldHead;
        head = node;

        if (oldHead == null) {
            tail = node;
        } else {
            oldHead.previous = node;
            relinkOperations++;
        }

        size++;
        return node;
    }

    public Node<T> addLast(T value) {
        Node<T> node = new Node<>(this, value);
        Node<T> oldTail = tail;

        node.previous = oldTail;
        tail = node;

        if (oldTail == null) {
            head = node;
        } else {
            oldTail.next = node;
            relinkOperations++;
        }

        size++;
        return node;
    }

    public T get(int index) {
        return nodeAt(index).value;
    }

    public T remove(Node<T> node) {
        requireOwnedLinkedNode(node);

        Node<T> previous = node.previous;
        Node<T> next = node.next;

        if (previous == null) {
            head = next;
        } else {
            previous.next = next;
            relinkOperations++;
        }

        if (next == null) {
            tail = previous;
        } else {
            next.previous = previous;
            relinkOperations++;
        }

        node.previous = null;
        node.next = null;
        node.linked = false;
        size--;
        return node.value;
    }

    private Node<T> nodeAt(int index) {
        requireElementIndex(index);

        if (index < size / 2) {
            Node<T> current = head;
            for (int i = 0; i < index; i++) {
                current = current.next;
                traversalSteps++;
            }
            return current;
        }

        Node<T> current = tail;
        for (int i = size - 1; i > index; i--) {
            current = current.previous;
            traversalSteps++;
        }
        return current;
    }

    private void requireElementIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
        }
    }

    private void requireOwnedLinkedNode(Node<T> node) {
        if (node == null || node.owner != this || !node.linked) {
            throw new IllegalArgumentException("node does not belong to this linked list or is already removed");
        }
    }

    public int size() {
        return size;
    }

    public long traversalSteps() {
        return traversalSteps;
    }

    public long relinkOperations() {
        return relinkOperations;
    }

    public void resetMetrics() {
        traversalSteps = 0;
        relinkOperations = 0;
    }
}
