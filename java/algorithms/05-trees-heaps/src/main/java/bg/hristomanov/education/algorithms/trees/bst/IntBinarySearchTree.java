package bg.hristomanov.education.algorithms.trees.bst;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Учебна Binary Search Tree implementation за уникални int стойности.
 *
 * <p>Основната цел е да се види, че search/insert/remove са O(h), където h е
 * height (височината) на дървото. Без balancing h може да стане O(n).</p>
 */
public final class IntBinarySearchTree {

    private Node root;
    private int size;

    public boolean add(int value) {
        if (root == null) {
            root = new Node(value);
            size = 1;
            return true;
        }

        Node current = root;
        while (true) {
            if (value == current.value) {
                return false;
            }

            if (value < current.value) {
                if (current.left == null) {
                    current.left = new Node(value);
                    size++;
                    return true;
                }
                current = current.left;
            } else {
                if (current.right == null) {
                    current.right = new Node(value);
                    size++;
                    return true;
                }
                current = current.right;
            }
        }
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
        RemovalResult result = remove(root, value);
        root = result.node;

        if (result.removed) {
            size--;
        }

        return result.removed;
    }

    private RemovalResult remove(Node node, int value) {
        if (node == null) {
            return new RemovalResult(null, false);
        }

        if (value < node.value) {
            RemovalResult result = remove(node.left, value);
            node.left = result.node;
            return new RemovalResult(node, result.removed);
        }

        if (value > node.value) {
            RemovalResult result = remove(node.right, value);
            node.right = result.node;
            return new RemovalResult(node, result.removed);
        }

        if (node.left == null) {
            return new RemovalResult(node.right, true);
        }

        if (node.right == null) {
            return new RemovalResult(node.left, true);
        }

        Node successor = minimum(node.right);
        node.value = successor.value;
        node.right = removeMinimum(node.right);
        return new RemovalResult(node, true);
    }

    private Node minimum(Node node) {
        Node current = node;
        while (current.left != null) {
            current = current.left;
        }
        return current;
    }

    private Node removeMinimum(Node node) {
        if (node.left == null) {
            return node.right;
        }

        node.left = removeMinimum(node.left);
        return node;
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

    public List<Integer> preorder() {
        List<Integer> values = new ArrayList<>(size);
        preorder(root, values);
        return List.copyOf(values);
    }

    private void preorder(Node node, List<Integer> values) {
        if (node == null) {
            return;
        }

        values.add(node.value);
        preorder(node.left, values);
        preorder(node.right, values);
    }

    public List<Integer> postorder() {
        List<Integer> values = new ArrayList<>(size);
        postorder(root, values);
        return List.copyOf(values);
    }

    private void postorder(Node node, List<Integer> values) {
        if (node == null) {
            return;
        }

        postorder(node.left, values);
        postorder(node.right, values);
        values.add(node.value);
    }

    public List<Integer> levelOrder() {
        if (root == null) {
            return List.of();
        }

        List<Integer> values = new ArrayList<>(size);
        Deque<Node> queue = new ArrayDeque<>();
        queue.addLast(root);

        while (!queue.isEmpty()) {
            Node current = queue.removeFirst();
            values.add(current.value);

            if (current.left != null) {
                queue.addLast(current.left);
            }
            if (current.right != null) {
                queue.addLast(current.right);
            }
        }

        return List.copyOf(values);
    }

    public int height() {
        return height(root);
    }

    private int height(Node node) {
        if (node == null) {
            return 0;
        }

        return 1 + Math.max(height(node.left), height(node.right));
    }

    public Integer rootValue() {
        return root == null ? null : root.value;
    }

    public int size() {
        return size;
    }

    private static final class Node {
        private int value;
        private Node left;
        private Node right;

        private Node(int value) {
            this.value = value;
        }
    }

    private record RemovalResult(Node node, boolean removed) {
    }
}
