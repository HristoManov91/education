package bg.hristomanov.education.patterns.behavioral.iterator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IteratorPatternTest {

    @Test
    void consumerTraversesPagedSourceWithoutKnowingPaginationMechanics() {
        InMemoryOrderPageSource source = new InMemoryOrderPageSource(
                List.of(
                        new OrderSummary("O-1", "PAID"),
                        new OrderSummary("O-2", "PAID"),
                        new OrderSummary("O-3", "NEW"),
                        new OrderSummary("O-4", "PAID"),
                        new OrderSummary("O-5", "CANCELLED"),
                        new OrderSummary("O-6", "PAID"),
                        new OrderSummary("O-7", "NEW")
                )
        );
        PagedOrderIterable orders = new PagedOrderIterable(source, 3);
        List<String> ids = new ArrayList<>();

        for (OrderSummary order : orders) {
            ids.add(order.orderId());
        }

        assertThat(ids).containsExactly(
                "O-1", "O-2", "O-3", "O-4", "O-5", "O-6", "O-7"
        );
        assertThat(source.fetchCount()).isEqualTo(3);
    }
}
