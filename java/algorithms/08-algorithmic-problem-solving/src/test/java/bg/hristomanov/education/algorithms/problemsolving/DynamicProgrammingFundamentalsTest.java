package bg.hristomanov.education.algorithms.problemsolving;

import bg.hristomanov.education.algorithms.problemsolving.dp.fundamentals.FibonacciDynamicProgramming;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicProgrammingFundamentalsTest {

    @Test
    void memoizationAndTabulationRemoveRepeatedSubproblemExplosion() {
        FibonacciDynamicProgramming fibonacci = new FibonacciDynamicProgramming();

        FibonacciDynamicProgramming.Result naive = fibonacci.naive(20);
        FibonacciDynamicProgramming.Result memoized = fibonacci.memoized(20);
        FibonacciDynamicProgramming.Result tabulated = fibonacci.tabulated(20);

        assertThat(naive.value()).isEqualTo(6_765);
        assertThat(memoized.value()).isEqualTo(naive.value());
        assertThat(tabulated.value()).isEqualTo(naive.value());

        assertThat(naive.evaluationsOrCalls())
                .isGreaterThan(memoized.evaluationsOrCalls() * 100);
        assertThat(memoized.evaluationsOrCalls()).isLessThan(50);
        assertThat(tabulated.evaluationsOrCalls()).isEqualTo(21);
    }
}
