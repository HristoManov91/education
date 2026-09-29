package bg.hristomanov.education.hexagonal;

import bg.hristomanov.education.hexagonal.application.OrderApplicationService;
import bg.hristomanov.education.hexagonal.domain.Order;
import bg.hristomanov.education.hexagonal.domain.Product;
import bg.hristomanov.education.hexagonal.port.in.OrderView;
import bg.hristomanov.education.hexagonal.port.in.PlaceOrderCommand;
import bg.hristomanov.education.hexagonal.port.out.OrderRepositoryPort;
import bg.hristomanov.education.hexagonal.port.out.ProductCatalogPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderApplicationServiceTest {

    @Test
    void useCaseRunsWithPlainInMemoryAdaptersAndNoSpringContext() {
        InMemoryOrderRepository orders = new InMemoryOrderRepository();
        InMemoryProductCatalog products = new InMemoryProductCatalog(
                Map.of(
                        "KEYBOARD",
                        new Product(
                                "KEYBOARD",
                                "Mechanical Keyboard",
                                new BigDecimal("100.00")
                        ),
                        "MOUSE",
                        new Product(
                                "MOUSE",
                                "Wireless Mouse",
                                new BigDecimal("40.00")
                        )
                )
        );

        OrderApplicationService application =
                new OrderApplicationService(orders, products);

        OrderView created = application.place(
                new PlaceOrderCommand(
                        "ORD-HEX-001",
                        "C-42",
                        List.of(
                                new PlaceOrderCommand.Item("KEYBOARD", 1),
                                new PlaceOrderCommand.Item("MOUSE", 2)
                        )
                )
        );

        assertThat(created.id()).isPositive();
        assertThat(created.totalAmount()).isEqualByComparingTo("180.00");
        assertThat(created.lines()).hasSize(2);

        OrderView reloaded = application.get(created.id());

        assertThat(reloaded).isEqualTo(created);
    }

    @Test
    void coreRejectsUnknownProductThroughTheOutputPortContract() {
        OrderApplicationService application =
                new OrderApplicationService(
                        new InMemoryOrderRepository(),
                        new InMemoryProductCatalog(Map.of())
                );

        assertThatThrownBy(() ->
                application.place(
                        new PlaceOrderCommand(
                                "ORD-HEX-UNKNOWN",
                                "C-42",
                                List.of(
                                        new PlaceOrderCommand.Item(
                                                "UNKNOWN",
                                                1
                                        )
                                )
                        )
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown product");
    }

    /**
     * Test adapter for the same driven port implemented by JPA in production.
     */
    private static final class InMemoryOrderRepository
            implements OrderRepositoryPort {

        private final AtomicLong sequence = new AtomicLong();
        private final Map<Long, Order> orders = new LinkedHashMap<>();

        @Override
        public Order save(Order order) {
            long id = order.id() == null
                    ? sequence.incrementAndGet()
                    : order.id();

            Order persisted = order.withId(id);
            orders.put(id, persisted);
            return persisted;
        }

        @Override
        public Optional<Order> findById(long orderId) {
            return Optional.ofNullable(orders.get(orderId));
        }
    }

    private record InMemoryProductCatalog(
            Map<String, Product> products
    ) implements ProductCatalogPort {

        @Override
        public Optional<Product> findBySku(String sku) {
            return Optional.ofNullable(products.get(sku));
        }
    }
}
