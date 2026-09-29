package bg.hristomanov.education.uow.domain;

import java.util.Optional;

/**
 * Domain/application-facing Repository contract.
 *
 * <p>Consumer-ът не знае EntityManager, JPQL, Hibernate Session или SQL.
 * Contract-ът е изразен на езика на aggregate-а.</p>
 */
public interface OrderRepository {

    void add(PurchaseOrder order);

    Optional<PurchaseOrder> findById(long orderId);

    Optional<PurchaseOrder> findByReference(String reference);

    default PurchaseOrder getRequired(long orderId) {
        return findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown order: " + orderId));
    }
}
