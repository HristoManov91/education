package bg.hristomanov.education.saga.service;

import bg.hristomanov.education.saga.domain.InventoryReservation;
import bg.hristomanov.education.saga.domain.ParticipantStatus;
import bg.hristomanov.education.saga.repository.InventoryReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryParticipantService {

    private final InventoryReservationRepository repository;
    private final SagaTrace trace;

    public InventoryParticipantService(
            InventoryReservationRepository repository,
            SagaTrace trace
    ) {
        this.repository = repository;
        this.trace = trace;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(Long sagaId, boolean fail) {
        if (repository.findBySagaId(sagaId).isPresent()) {
            return;
        }

        trace.add("inventory:reserve");

        if (fail) {
            throw new SagaStepException("Inventory reservation failed");
        }

        repository.saveAndFlush(new InventoryReservation(sagaId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Long sagaId) {
        InventoryReservation reservation = repository.findBySagaId(sagaId)
                .orElse(null);

        if (reservation == null
                || reservation.getStatus() == ParticipantStatus.RELEASED) {
            return;
        }

        trace.add("inventory:release");
        reservation.release();
    }

    @Transactional(readOnly = true)
    public ParticipantStatus status(Long sagaId) {
        return repository.findBySagaId(sagaId)
                .map(InventoryReservation::getStatus)
                .orElse(null);
    }
}
