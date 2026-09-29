package bg.hristomanov.education.saga.service;

import bg.hristomanov.education.saga.domain.OrderSaga;
import bg.hristomanov.education.saga.repository.OrderSagaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SagaStateService {

    private final OrderSagaRepository repository;

    public SagaStateService(OrderSagaRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(String orderReference) {
        return repository.saveAndFlush(new OrderSaga(orderReference)).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long sagaId) {
        required(sagaId).complete();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startCompensation(Long sagaId, String reason) {
        required(sagaId).startCompensation(reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reject(Long sagaId) {
        required(sagaId).reject();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requireCompensationRetry(Long sagaId, String reason) {
        required(sagaId).requireCompensationRetry(reason);
    }

    @Transactional(readOnly = true)
    public OrderSaga get(Long sagaId) {
        return required(sagaId);
    }

    private OrderSaga required(Long sagaId) {
        return repository.findById(sagaId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown saga: " + sagaId));
    }
}
