package bg.hristomanov.education.cqrs.write;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderWriteRepository
        extends JpaRepository<OrderWriteEntity, Long> {
}
