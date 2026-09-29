package bg.hristomanov.education.uow.service;

import bg.hristomanov.education.uow.domain.PurchaseOrder;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Baseline без Repository abstraction.
 *
 * <p>Този код не е "забранен". За малък use case може да е напълно достатъчен.
 * Цената е, че application service-ът вече знае persistence API details.
 * Repository pattern става полезен, когато искаме ясна aggregate-oriented
 * boundary, reuse на retrieval semantics или independence от конкретния store API.</p>
 */
@Service
public class DirectEntityManagerOrderService {

    private final EntityManager entityManager;

    public DirectEntityManagerOrderService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void markPaid(long orderId) {
        PurchaseOrder order = entityManager.find(PurchaseOrder.class, orderId);
        if (order == null) {
            throw new IllegalArgumentException("Unknown order: " + orderId);
        }
        order.markPaid();
    }
}
