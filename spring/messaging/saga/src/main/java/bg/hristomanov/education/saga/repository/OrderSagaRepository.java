package bg.hristomanov.education.saga.repository;

import bg.hristomanov.education.saga.domain.OrderSaga;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSagaRepository extends JpaRepository<OrderSaga, Long> {
}
