package bg.hristomanov.education.events.repository;

import bg.hristomanov.education.events.projection.OrderPaidProjection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPaidProjectionRepository
        extends JpaRepository<OrderPaidProjection, Long> {
}
