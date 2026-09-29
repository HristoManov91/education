package bg.hristomanov.education.hexagonal.adapter.out.jpa;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataOrderRepository
        extends JpaRepository<JpaOrderEntity, Long> {

    @Override
    @EntityGraph(attributePaths = "lines")
    Optional<JpaOrderEntity> findById(Long orderId);
}
