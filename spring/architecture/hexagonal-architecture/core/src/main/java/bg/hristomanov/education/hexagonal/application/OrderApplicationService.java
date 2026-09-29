package bg.hristomanov.education.hexagonal.application;

import bg.hristomanov.education.hexagonal.domain.Order;
import bg.hristomanov.education.hexagonal.domain.OrderLine;
import bg.hristomanov.education.hexagonal.domain.Product;
import bg.hristomanov.education.hexagonal.port.in.GetOrderUseCase;
import bg.hristomanov.education.hexagonal.port.in.OrderView;
import bg.hristomanov.education.hexagonal.port.in.PlaceOrderCommand;
import bg.hristomanov.education.hexagonal.port.in.PlaceOrderUseCase;
import bg.hristomanov.education.hexagonal.port.out.OrderRepositoryPort;
import bg.hristomanov.education.hexagonal.port.out.ProductCatalogPort;

import java.util.List;

/**
 * Application core: plain Java, без Spring/JPA/Web annotations.
 */
public final class OrderApplicationService
        implements PlaceOrderUseCase, GetOrderUseCase {

    private final OrderRepositoryPort orderRepository;
    private final ProductCatalogPort productCatalog;

    public OrderApplicationService(
            OrderRepositoryPort orderRepository,
            ProductCatalogPort productCatalog
    ) {
        this.orderRepository = orderRepository;
        this.productCatalog = productCatalog;
    }

    @Override
    public OrderView place(PlaceOrderCommand command) {
        if (command.items().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        List<OrderLine> lines = command.items()
                .stream()
                .map(this::toOrderLine)
                .toList();

        Order order = Order.place(
                command.reference(),
                command.customerId(),
                lines
        );

        Order persisted = orderRepository.save(order);

        return OrderView.from(persisted);
    }

    @Override
    public OrderView get(long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Unknown order: " + orderId)
                );

        return OrderView.from(order);
    }

    private OrderLine toOrderLine(PlaceOrderCommand.Item item) {
        Product product = productCatalog.getRequired(item.sku());

        return new OrderLine(
                product.sku(),
                product.name(),
                item.quantity(),
                product.unitPrice()
        );
    }
}
