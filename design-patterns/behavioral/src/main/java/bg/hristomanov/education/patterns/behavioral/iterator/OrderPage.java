package bg.hristomanov.education.patterns.behavioral.iterator;

import java.util.List;

public record OrderPage(
        List<OrderSummary> items,
        boolean hasMore
) {
    public OrderPage {
        items = List.copyOf(items);
    }
}
