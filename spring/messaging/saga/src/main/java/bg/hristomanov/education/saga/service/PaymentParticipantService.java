package bg.hristomanov.education.saga.service;

import bg.hristomanov.education.saga.domain.ParticipantStatus;
import bg.hristomanov.education.saga.domain.PaymentReservation;
import bg.hristomanov.education.saga.repository.PaymentReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentParticipantService {

    private final PaymentReservationRepository repository;
    private final SagaTrace trace;

    public PaymentParticipantService(
            PaymentReservationRepository repository,
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

        trace.add("payment:reserve");

        if (fail) {
            throw new SagaStepException("Payment reservation failed");
        }

        repository.saveAndFlush(new PaymentReservation(sagaId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Long sagaId, boolean fail) {
        PaymentReservation reservation = repository.findBySagaId(sagaId)
                .orElse(null);

        if (reservation == null
                || reservation.getStatus() == ParticipantStatus.RELEASED) {
            return;
        }

        trace.add("payment:release");

        if (fail) {
            throw new SagaCompensationException("Payment compensation failed");
        }

        reservation.release();
    }

    @Transactional(readOnly = true)
    public ParticipantStatus status(Long sagaId) {
        return repository.findBySagaId(sagaId)
                .map(PaymentReservation::getStatus)
                .orElse(null);
    }
}
