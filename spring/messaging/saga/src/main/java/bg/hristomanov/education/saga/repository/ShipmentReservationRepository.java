package bg.hristomanov.education.saga.repository;

import bg.hristomanov.education.saga.domain.ShipmentReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShipmentReservationRepository
        extends JpaRepository<ShipmentReservation, Long> {

    Optional<ShipmentReservation> findBySagaId(Long sagaId);
}
