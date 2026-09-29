package bg.hristomanov.education.hexagonal.port.out;

import bg.hristomanov.education.hexagonal.domain.Order;

import java.util.Optional;

/**
 * Driven/output port owned by the application core.
 *
 * <p>Core-ът казва какъв persistence conversation му трябва.
 * JPA adapter-ът по-късно ще implement-не този contract.</p>
 */
public interface OrderRepositoryPort {

    Order save(Order order);

    Optional<Order> findById(long orderId);
}
