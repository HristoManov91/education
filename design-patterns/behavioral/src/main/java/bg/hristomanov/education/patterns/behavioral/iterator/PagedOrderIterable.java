package bg.hristomanov.education.patterns.behavioral.iterator;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterator скрива traversal mechanics (механиката на обхождане).
 *
 * <p>Consumer-ът използва normal for-each и не знае нищо за page numbers,
 * page size, lazy loading или кога се прави следващият remote/DB fetch.</p>
 */
public class PagedOrderIterable implements Iterable<OrderSummary> {

    private final OrderPageSource source;
    private final int pageSize;

    public PagedOrderIterable(OrderPageSource source, int pageSize) {
        this.source = source;
        this.pageSize = pageSize;
    }

    @Override
    public Iterator<OrderSummary> iterator() {
        return new PagedOrderIterator(source, pageSize);
    }

    private static final class PagedOrderIterator implements Iterator<OrderSummary> {

        private final OrderPageSource source;
        private final int pageSize;

        private int nextPageNumber;
        private Iterator<OrderSummary> current = List.<OrderSummary>of().iterator();
        private boolean initialized;
        private boolean hasMore = true;

        private PagedOrderIterator(OrderPageSource source, int pageSize) {
            this.source = source;
            this.pageSize = pageSize;
        }

        @Override
        public boolean hasNext() {
            ensureAvailable();
            return current.hasNext();
        }

        @Override
        public OrderSummary next() {
            ensureAvailable();
            if (!current.hasNext()) {
                throw new NoSuchElementException();
            }
            return current.next();
        }

        private void ensureAvailable() {
            if (!initialized) {
                loadNextPage();
                initialized = true;
            }

            while (!current.hasNext() && hasMore) {
                loadNextPage();
            }
        }

        private void loadNextPage() {
            OrderPage page = source.fetchPage(nextPageNumber, pageSize);
            nextPageNumber++;
            current = page.items().iterator();
            hasMore = page.hasMore();
        }
    }
}
