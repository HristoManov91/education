package bg.hristomanov.education.algorithms.linear;

import bg.hristomanov.education.algorithms.linear.deque.CircularArrayDeque;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CircularArrayDequeTest {

    @Test
    void sameDequeCanProvideQueueSemantics() {
        CircularArrayDeque<String> queue = new CircularArrayDeque<>();

        queue.addLast("first");
        queue.addLast("second");
        queue.addLast("third");

        assertThat(queue.removeFirst()).isEqualTo("first");
        assertThat(queue.removeFirst()).isEqualTo("second");
        assertThat(queue.removeFirst()).isEqualTo("third");
    }

    @Test
    void sameDequeCanProvideStackSemantics() {
        CircularArrayDeque<String> stack = new CircularArrayDeque<>();

        stack.addLast("first");
        stack.addLast("second");
        stack.addLast("third");

        assertThat(stack.removeLast()).isEqualTo("third");
        assertThat(stack.removeLast()).isEqualTo("second");
        assertThat(stack.removeLast()).isEqualTo("first");
    }

    @Test
    void circularIndexingSurvivesWrapAroundAndGrowth() {
        CircularArrayDeque<Integer> deque = new CircularArrayDeque<>(3);

        deque.addLast(1);
        deque.addLast(2);
        deque.addLast(3);
        assertThat(deque.removeFirst()).isEqualTo(1);

        deque.addLast(4);
        deque.addFirst(0);

        assertThat(deque.capacity()).isEqualTo(6);
        assertThat(deque.removeFirst()).isEqualTo(0);
        assertThat(deque.removeFirst()).isEqualTo(2);
        assertThat(deque.removeFirst()).isEqualTo(3);
        assertThat(deque.removeFirst()).isEqualTo(4);
        assertThat(deque.isEmpty()).isTrue();
    }
}
