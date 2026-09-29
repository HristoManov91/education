package bg.hristomanov.education.uow;

import bg.hristomanov.education.uow.domain.OrderRepository;
import bg.hristomanov.education.uow.domain.PurchaseOrder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository contract може да има test implementation без JPA.
 *
 * <p>Това не доказва, че винаги трябва да mock-ваме repositories.
 * Integration tests върху реалния persistence adapter остават важни.
 * Тестът показва architectural dependency direction-а.</p>
 */
class RepositoryBoundaryTest {

    @Test
    void applicationFacingRepositoryContractDoesNotRequireJpaApi() {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();

        PurchaseOrder order = new PurchaseOrder(
                "ORD-IN-MEMORY",
                new java.math.BigDecimal("42.00")
        );

        repository.add(order);

        assertThat(repository.findByReference("ORD-IN-MEMORY"))
                .containsSame(order);
    }

    private static final class InMemoryOrderRepository implements OrderRepository {

        private final AtomicLong sequence = new AtomicLong();
        private final Map<Long, PurchaseOrder> orders = new HashMap<>();

        @Override
        public void add(PurchaseOrder order) {
            orders.put(sequence.incrementAndGet(), order);
        }

        @Override
        public Optional<PurchaseOrder> findById(long orderId) {
            return Optional.ofNullable(orders.get(orderId));
        }

        @Override
        public Optional<PurchaseOrder> findByReference(String reference) {
            return orders.values()
                    .stream()
                    .filter(order -> order.getReference().equals(reference))
                    .findFirst();
        }
    }
}
