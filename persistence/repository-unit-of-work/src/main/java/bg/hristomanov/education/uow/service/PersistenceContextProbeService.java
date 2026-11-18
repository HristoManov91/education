package bg.hristomanov.education.uow.service;

import bg.hristomanov.education.uow.domain.OrderRepository;
import bg.hristomanov.education.uow.domain.PurchaseOrder;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Учебен service за директно доказване на persistence-context semantics.
 *
 * <p>В нормален application service не бихме inject-вали EntityManager само
 * за диагностика. Тук го правим умишлено, защото това е лаборатория за
 * Unit of Work / Identity Map / flush / detach.</p>
 */
@Service
public class PersistenceContextProbeService {

    private final OrderRepository orderRepository;
    private final EntityManager entityManager;

    public PersistenceContextProbeService(
            OrderRepository orderRepository,
            EntityManager entityManager
    ) {
        this.orderRepository = orderRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public boolean twoLoadsReturnSameManagedInstance(long orderId) {
        PurchaseOrder first = orderRepository.getRequired(orderId);
        PurchaseOrder second = orderRepository.getRequired(orderId);

        return first == second;
    }

    @Transactional
    public void markPaidFlushThenFail(long orderId) {
        PurchaseOrder order = orderRepository.getRequired(orderId);
        order.markPaid();

        /*
         * flush() синхронизира SQL към DB connection-а, но НЕ commit-ва transaction-а.
         * След exception Spring rollback-ва transaction-а и UPDATE-ът не остава.
         */
        entityManager.flush();

        throw new IllegalStateException("Simulated failure after flush");
    }

    @Transactional
    public void detachThenChange(long orderId) {
        PurchaseOrder order = orderRepository.getRequired(orderId);

        /*
         * clear() detach-ва всички managed entities от persistence context-а.
         * Следващата domain промяна вече не се следи от dirty checking.
         */
        entityManager.clear();
        order.markPaid();
    }

    /**
     * merge() COPIES detached state into a managed entity; it never re-attaches
     * the argument itself. The returned instance owns subsequent dirty checking.
     */
    @Transactional
    public boolean mergeDetachedChangeAndCheckIdentity(long orderId) {
        PurchaseOrder detached = orderRepository.getRequired(orderId);
        entityManager.detach(detached);
        detached.markPaid();
        PurchaseOrder managed = entityManager.merge(detached);
        boolean distinctManagedInstance = detached != managed
                && !entityManager.contains(detached)
                && entityManager.contains(managed);
        entityManager.flush();
        return distinctManagedInstance;
    }

    /**
     * A scalar JPQL query against the same table must observe the pending state;
     * Hibernate AUTO flush synchronizes the UPDATE before this SQL SELECT.
     */
    @Transactional
    public boolean autoFlushBeforeOverlappingScalarQuery(long orderId) {
        PurchaseOrder order = orderRepository.getRequired(orderId);
        order.markPaid();
        var databaseStatus = entityManager.createQuery(
                        "select o.status from PurchaseOrder o where o.id = :id",
                        bg.hristomanov.education.uow.domain.OrderStatus.class)
                .setParameter("id", orderId)
                .getSingleResult();
        return databaseStatus == bg.hristomanov.education.uow.domain.OrderStatus.PAID;
    }
}
