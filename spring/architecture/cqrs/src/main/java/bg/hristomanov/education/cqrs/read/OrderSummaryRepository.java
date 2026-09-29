package bg.hristomanov.education.cqrs.read;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderSummaryRepository
        extends JpaRepository<OrderSummaryProjection, Long> {

    List<OrderSummaryProjection> findByCustomerIdOrderByProjectedAtDesc(
            String customerId
    );
}
