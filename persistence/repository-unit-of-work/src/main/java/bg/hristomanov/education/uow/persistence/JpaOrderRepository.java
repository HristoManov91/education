package bg.hristomanov.education.uow.persistence;

import bg.hristomanov.education.uow.domain.OrderRepository;
import bg.hristomanov.education.uow.domain.PurchaseOrder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA adapter зад Repository boundary-то.
 */
@Repository
public class JpaOrderRepository implements OrderRepository {

    private final EntityManager entityManager;

    public JpaOrderRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void add(PurchaseOrder order) {
        entityManager.persist(order);
    }

    @Override
    public Optional<PurchaseOrder> findById(long orderId) {
        return Optional.ofNullable(entityManager.find(PurchaseOrder.class, orderId));
    }

    @Override
    public Optional<PurchaseOrder> findByReference(String reference) {
        try {
            PurchaseOrder order = entityManager.createQuery(
                            "select o from PurchaseOrder o where o.reference = :reference",
                            PurchaseOrder.class
                    )
                    .setParameter("reference", reference)
                    .getSingleResult();

            return Optional.of(order);
        } catch (NoResultException exception) {
            return Optional.empty();
        }
    }
}
