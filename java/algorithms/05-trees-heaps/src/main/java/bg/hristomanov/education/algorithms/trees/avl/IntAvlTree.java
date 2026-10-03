package bg.hristomanov.education.algorithms.trees.avl;

import java.util.ArrayList;
import java.util.List;

/**
 * Учебна AVL tree implementation за уникални int стойности.
 *
 * <p>AVL пази height-balance чрез rotations. Допълнителният bookkeeping
 * (поддържане на height + rebalancing) купува гаранция за O(log n) height.</p>
 */
public final class IntAvlTree {

    private Node root;
    private int size;
    private long rotations;

    public boolean add(int value) {
        MutableFlag inserted = new MutableFlag();
        root = insert(root, value, inserted);

        if (inserted.value) {
            size++;
        }

        return inserted.value;
    }

    private Node insert(Node node, int value, MutableFlag inserted) {
        if (node == null) {
            inserted.value = true;
            return new Node(value);
        }

        if (value < node.value) {
            node.left = insert(node.left, value, inserted);
        } else if (value > node.value) {
            node.right = insert(node.right, value, inserted);
        } else {
            return node;
        }

        return rebalance(node);
    }

    public boolean contains(int value) {
        Node current = root;

        while (current != null) {
            if (value == current.value) {
                return true;
            }

            current = value < current.value ? current.left : current.right;
        }

        return false;
    }

    public boolean remove(int value) {
        MutableFlag removed = new MutableFlag();
        root = remove(root, value, removed);

        if (removed.value) {
            size--;
        }

        return removed.value;
    }

    private Node remove(Node node, int value, MutableFlag removed) {
        if (node == null) {
            return null;
        }

        if (value < node.value) {
            node.left = remove(node.left, value, removed);
        } else if (value > node.value) {
            node.right = remove(node.right, value, removed);
        } else {
            removed.value = true;

            if (node.left == null) {
                return node.right;
            }

            if (node.right == null) {
                return node.left;
            }

            Node successor = minimum(node.right);
            node.value = successor.value;
            node.right = removeMinimum(node.right);
        }

        return rebalance(node);
    }

    private Node removeMinimum(Node node) {
        if (node.left == null) {
            return node.right;
        }

        node.left = removeMinimum(node.left);
        return rebalance(node);
    }

    private Node minimum(Node node) {
        Node current = node;
        while (current.left != null) {
            current = current.left;
        }
        return current;
    }

    private Node rebalance(Node node) {
        updateHeight(node);
        int balance = balanceFactor(node);

        if (balance > 1) {
            if (balanceFactor(node.left) < 0) {
                node.left = rotateLeft(node.left);
            }
            return rotateRight(node);
        }

        if (balance < -1) {
            if (balanceFactor(node.right) > 0) {
                node.right = rotateRight(node.right);
            }
            return rotateLeft(node);
        }

        return node;
    }

    private Node rotateLeft(Node oldRoot) {
        Node newRoot = oldRoot.right;
        Node transferredSubtree = newRoot.left;

        newRoot.left = oldRoot;
        oldRoot.right = transferredSubtree;

        updateHeight(oldRoot);
        updateHeight(newRoot);
        rotations++;
        return newRoot;
    }

    private Node rotateRight(Node oldRoot) {
        Node newRoot = oldRoot.left;
        Node transferredSubtree = newRoot.right;

        newRoot.right = oldRoot;
        oldRoot.left = transferredSubtree;

        updateHeight(oldRoot);
        updateHeight(newRoot);
        rotations++;
        return newRoot;
    }

    private void updateHeight(Node node) {
        node.height = 1 + Math.max(height(node.left), height(node.right));
    }

    private int balanceFactor(Node node) {
        return height(node.left) - height(node.right);
    }

    private int height(Node node) {
        return node == null ? 0 : node.height;
    }

    public int height() {
        return height(root);
    }

    public List<Integer> inorder() {
        List<Integer> values = new ArrayList<>(size);
        inorder(root, values);
        return List.copyOf(values);
    }

    private void inorder(Node node, List<Integer> values) {
        if (node == null) {
            return;
        }

        inorder(node.left, values);
        values.add(node.value);
        inorder(node.right, values);
    }

    /**
     * Проверка за учебните тестове: едновременно BST ordering, stored heights
     * и AVL balance condition.
     */
    public boolean isValidAvl() {
        return validate(root, Long.MIN_VALUE, Long.MAX_VALUE) >= 0;
    }

    private int validate(Node node, long minimumExclusive, long maximumExclusive) {
        if (node == null) {
            return 0;
        }

        if (node.value <= minimumExclusive || node.value >= maximumExclusive) {
            return -1;
        }

        int leftHeight = validate(node.left, minimumExclusive, node.value);
        if (leftHeight < 0) {
            return -1;
        }

        int rightHeight = validate(node.right, node.value, maximumExclusive);
        if (rightHeight < 0) {
            return -1;
        }

        int calculatedHeight = 1 + Math.max(leftHeight, rightHeight);
        if (node.height != calculatedHeight || Math.abs(leftHeight - rightHeight) > 1) {
            return -1;
        }

        return calculatedHeight;
    }

    public Integer rootValue() {
        return root == null ? null : root.value;
    }

    public int size() {
        return size;
    }

    public long rotations() {
        return rotations;
    }

    public void resetRotationCount() {
        rotations = 0;
    }

    private static final class Node {
        private int value;
        private int height = 1;
        private Node left;
        private Node right;

        private Node(int value) {
            this.value = value;
        }
    }

    private static final class MutableFlag {
        private boolean value;
    }
}
