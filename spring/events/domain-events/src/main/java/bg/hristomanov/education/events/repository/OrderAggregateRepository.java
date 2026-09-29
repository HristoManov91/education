package bg.hristomanov.education.events.repository;

import bg.hristomanov.education.events.domain.OrderAggregate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderAggregateRepository
        extends JpaRepository<OrderAggregate, Long> {
}
