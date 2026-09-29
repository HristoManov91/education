package bg.hristomanov.education.patterns.behavioral.iterator;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryOrderPageSource implements OrderPageSource {

    private final List<OrderSummary> orders;
    private final AtomicInteger fetchCount = new AtomicInteger();

    public InMemoryOrderPageSource(List<OrderSummary> orders) {
        this.orders = List.copyOf(orders);
    }

    @Override
    public OrderPage fetchPage(int pageNumber, int pageSize) {
        fetchCount.incrementAndGet();

        int fromIndex = pageNumber * pageSize;
        if (fromIndex >= orders.size()) {
            return new OrderPage(List.of(), false);
        }

        int toIndex = Math.min(fromIndex + pageSize, orders.size());
        List<OrderSummary> items = orders.subList(fromIndex, toIndex);
        boolean hasMore = toIndex < orders.size();

        return new OrderPage(items, hasMore);
    }

    public int fetchCount() {
        return fetchCount.get();
    }
}
