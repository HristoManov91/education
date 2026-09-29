package bg.hristomanov.education.saga.repository;

import bg.hristomanov.education.saga.domain.PaymentReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentReservationRepository
        extends JpaRepository<PaymentReservation, Long> {

    Optional<PaymentReservation> findBySagaId(Long sagaId);
}
