package bg.hristomanov.education.algorithms.trees;

import bg.hristomanov.education.algorithms.trees.bst.IntBinarySearchTree;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BinarySearchTreeTest {

    @Test
    void traversalsExposeDifferentTreeOrders() {
        IntBinarySearchTree tree = sampleTree();

        assertThat(tree.inorder()).containsExactly(1, 3, 6, 8, 10, 12, 14);
        assertThat(tree.preorder()).containsExactly(8, 3, 1, 6, 12, 10, 14);
        assertThat(tree.postorder()).containsExactly(1, 6, 3, 10, 14, 12, 8);
        assertThat(tree.levelOrder()).containsExactly(8, 3, 12, 1, 6, 10, 14);
    }

    @Test
    void removalHandlesLeafOneChildAndTwoChildren() {
        IntBinarySearchTree tree = new IntBinarySearchTree();
        tree.add(8);
        tree.add(3);
        tree.add(12);
        tree.add(1);
        tree.add(6);
        tree.add(10);
        tree.add(14);
        tree.add(13);

        assertThat(tree.remove(1)).isTrue();   // leaf
        assertThat(tree.remove(14)).isTrue();  // one child: 13
        assertThat(tree.remove(8)).isTrue();   // two children

        assertThat(tree.inorder()).containsExactly(3, 6, 10, 12, 13);
        assertThat(tree.contains(8)).isFalse();
        assertThat(tree.size()).isEqualTo(5);
    }

    @Test
    void sortedInsertionCanDegenerateOrdinaryBstToLinearHeight() {
        IntBinarySearchTree tree = new IntBinarySearchTree();

        for (int value = 1; value <= 1_000; value++) {
            tree.add(value);
        }

        assertThat(tree.height()).isEqualTo(1_000);
        assertThat(tree.rootValue()).isEqualTo(1);
    }

    private IntBinarySearchTree sampleTree() {
        IntBinarySearchTree tree = new IntBinarySearchTree();
        int[] values = {8, 3, 12, 1, 6, 10, 14};

        for (int value : values) {
            tree.add(value);
        }

        return tree;
    }
}
