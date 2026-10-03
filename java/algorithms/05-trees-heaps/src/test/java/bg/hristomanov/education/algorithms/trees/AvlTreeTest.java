package bg.hristomanov.education.algorithms.trees;

import bg.hristomanov.education.algorithms.trees.avl.IntAvlTree;
import bg.hristomanov.education.algorithms.trees.bst.IntBinarySearchTree;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AvlTreeTest {

    @Test
    void sortedInsertionKeepsAvlHeightLogarithmicWhilePlainBstDegenerates() {
        IntBinarySearchTree plainBst = new IntBinarySearchTree();
        IntAvlTree avl = new IntAvlTree();

        for (int value = 1; value <= 1_000; value++) {
            plainBst.add(value);
            avl.add(value);
        }

        assertThat(plainBst.height()).isEqualTo(1_000);
        assertThat(avl.height()).isLessThanOrEqualTo(12);
        assertThat(avl.isValidAvl()).isTrue();
        assertThat(avl.inorder()).containsExactlyElementsOf(plainBst.inorder());
    }

    @Test
    void allFourClassicRotationShapesProduceTheSameBalancedRoot() {
        assertBalancedRoot(new int[]{30, 20, 10}); // LL
        assertBalancedRoot(new int[]{10, 20, 30}); // RR
        assertBalancedRoot(new int[]{30, 10, 20}); // LR
        assertBalancedRoot(new int[]{10, 30, 20}); // RL
    }

    @Test
    void deletionRebalancesAncestorsAndPreservesBstOrdering() {
        IntAvlTree avl = new IntAvlTree();

        for (int value = 1; value <= 63; value++) {
            avl.add(value);
        }

        avl.resetRotationCount();

        for (int value = 1; value <= 31; value++) {
            assertThat(avl.remove(value)).isTrue();
        }

        assertThat(avl.isValidAvl()).isTrue();
        assertThat(avl.height()).isLessThanOrEqualTo(7);
        assertThat(avl.inorder().getFirst()).isEqualTo(32);
        assertThat(avl.inorder().getLast()).isEqualTo(63);
        assertThat(avl.rotations()).isGreaterThan(0);
    }

    private void assertBalancedRoot(int[] insertionOrder) {
        IntAvlTree avl = new IntAvlTree();

        for (int value : insertionOrder) {
            avl.add(value);
        }

        assertThat(avl.rootValue()).isEqualTo(20);
        assertThat(avl.height()).isEqualTo(2);
        assertThat(avl.isValidAvl()).isTrue();
        assertThat(avl.rotations()).isGreaterThan(0);
    }
}
