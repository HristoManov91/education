package bg.hristomanov.education.hexagonal.adapter.out.jpa;

import bg.hristomanov.education.hexagonal.domain.Order;
import bg.hristomanov.education.hexagonal.domain.OrderLine;
import bg.hristomanov.education.hexagonal.domain.OrderStatus;
import bg.hristomanov.education.hexagonal.port.out.OrderRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Driven adapter: translates between the core domain model and JPA model.
 *
 * <p>JPA annotations and Spring Data types stop here. The core never sees them.</p>
 */
@Repository
public class JpaOrderRepositoryAdapter
        implements OrderRepositoryPort {

    private final SpringDataOrderRepository repository;

    public JpaOrderRepositoryAdapter(
            SpringDataOrderRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public Order save(Order order) {
        JpaOrderEntity entity = toEntity(order);
        JpaOrderEntity saved = repository.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Order> findById(long orderId) {
        return repository.findById(orderId)
                .map(this::toDomain);
    }

    private JpaOrderEntity toEntity(Order order) {
        List<JpaOrderLineEntity> lines = order.lines()
                .stream()
                .map(line ->
                        new JpaOrderLineEntity(
                                line.sku(),
                                line.productName(),
                                line.quantity(),
                                line.unitPrice()
                        )
                )
                .toList();

        return new JpaOrderEntity(
                order.id(),
                order.reference(),
                order.customerId(),
                order.status().name(),
                order.totalAmount(),
                lines
        );
    }

    private Order toDomain(JpaOrderEntity entity) {
        List<OrderLine> lines = entity.getLines()
                .stream()
                .map(line ->
                        new OrderLine(
                                line.getSku(),
                                line.getProductName(),
                                line.getQuantity(),
                                line.getUnitPrice()
                        )
                )
                .toList();

        return Order.rehydrate(
                entity.getId(),
                entity.getReference(),
                entity.getCustomerId(),
                OrderStatus.valueOf(entity.getStatus()),
                lines
        );
    }
}
