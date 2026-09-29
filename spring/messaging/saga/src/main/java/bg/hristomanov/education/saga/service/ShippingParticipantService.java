package bg.hristomanov.education.saga.service;

import bg.hristomanov.education.saga.domain.ParticipantStatus;
import bg.hristomanov.education.saga.domain.ShipmentReservation;
import bg.hristomanov.education.saga.repository.ShipmentReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShippingParticipantService {

    private final ShipmentReservationRepository repository;
    private final SagaTrace trace;

    public ShippingParticipantService(
            ShipmentReservationRepository repository,
            SagaTrace trace
    ) {
        this.repository = repository;
        this.trace = trace;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void schedule(Long sagaId, boolean fail) {
        if (repository.findBySagaId(sagaId).isPresent()) {
            return;
        }

        trace.add("shipping:schedule");

        if (fail) {
            throw new SagaStepException("Shipping scheduling failed");
        }

        repository.saveAndFlush(new ShipmentReservation(sagaId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancel(Long sagaId) {
        ShipmentReservation reservation = repository.findBySagaId(sagaId)
                .orElse(null);

        if (reservation == null
                || reservation.getStatus() == ParticipantStatus.CANCELLED) {
            return;
        }

        trace.add("shipping:cancel");
        reservation.cancel();
    }

    @Transactional(readOnly = true)
    public ParticipantStatus status(Long sagaId) {
        return repository.findBySagaId(sagaId)
                .map(ShipmentReservation::getStatus)
                .orElse(null);
    }
}
