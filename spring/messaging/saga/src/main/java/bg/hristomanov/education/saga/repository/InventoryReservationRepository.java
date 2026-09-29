package bg.hristomanov.education.saga.repository;

import bg.hristomanov.education.saga.domain.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryReservationRepository
        extends JpaRepository<InventoryReservation, Long> {

    Optional<InventoryReservation> findBySagaId(Long sagaId);
}
