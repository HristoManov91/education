package bg.hristomanov.education.outbox.repository;

import bg.hristomanov.education.outbox.domain.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
}
