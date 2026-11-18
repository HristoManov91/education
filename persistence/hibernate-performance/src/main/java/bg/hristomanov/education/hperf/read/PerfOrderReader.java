package bg.hristomanov.education.hperf.read;

import bg.hristomanov.education.hperf.domain.PerfCustomer;
import bg.hristomanov.education.hperf.domain.PerfOrder;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Repository
public class PerfOrderReader {
    private final EntityManager entityManager;

    public PerfOrderReader(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<PerfOrder> naiveOrders() {
        return entityManager.createQuery(
                "select o from PerfOrder o order by o.id", PerfOrder.class).getResultList();
    }

    public List<PerfOrder> ordersWithCustomer() {
        return entityManager.createQuery(
                "select o from PerfOrder o join fetch o.customer order by o.id", PerfOrder.class)
                .getResultList();
    }

    public List<OrderRow> offsetPage(int offset, int size) {
        return entityManager.createQuery("""
                select new bg.hristomanov.education.hperf.read.OrderRow(
                    o.id, c.name, o.total, o.createdAt)
                from PerfOrder o join o.customer c
                order by o.createdAt desc, o.id desc
                """, OrderRow.class)
                .setFirstResult(offset).setMaxResults(size).getResultList();
    }

    /** Seek from a stable (createdAt,id) cursor, not a moving OFFSET. */
    public List<OrderRow> after(LocalDateTime timestamp, Long id, int size) {
        if (timestamp == null || id == null) {
            return offsetPage(0, size);
        }
        return entityManager.createQuery("""
                select new bg.hristomanov.education.hperf.read.OrderRow(
                    o.id, c.name, o.total, o.createdAt)
                from PerfOrder o join o.customer c
                where o.createdAt < :timestamp
                    or (o.createdAt = :timestamp and o.id < :id)
                order by o.createdAt desc, o.id desc
                """, OrderRow.class)
                .setParameter("timestamp", timestamp)
                .setParameter("id", id)
                .setMaxResults(size)
                .getResultList();
    }

    /** Intentional negative example: collection fetch + SQL pagination is unsafe. */
    public List<PerfCustomer> unsafeCollectionPage(int size) {
        return entityManager.createQuery("""
                select distinct c from PerfCustomer c
                left join fetch c.orders
                order by c.id
                """, PerfCustomer.class)
                .setMaxResults(size)
                .getResultList();
    }

    /** Page IDs first; then fetch their collections, reapply the desired order. */
    public List<PerfCustomer> collectionPageByIds(int size) {
        List<Long> ids = entityManager.createQuery(
                "select c.id from PerfCustomer c order by c.id", Long.class)
                .setMaxResults(size)
                .getResultList();
        if (ids.isEmpty()) return List.of();
        return entityManager.createQuery("""
                select distinct c from PerfCustomer c
                left join fetch c.orders
                where c.id in :ids
                """, PerfCustomer.class)
                .setParameter("ids", ids)
                .getResultList()
                .stream().sorted(Comparator.comparing(PerfCustomer::getId)).toList();
    }

    /** JPQL bulk writes bypass managed entity dirty checking / first-level cache. */
    public int bulkSetTotal(java.math.BigDecimal oldTotal, java.math.BigDecimal newTotal) {
        return entityManager.createQuery("""
                update PerfOrder o set o.total = :newTotal where o.total = :oldTotal
                """)
                .setParameter("newTotal", newTotal)
                .setParameter("oldTotal", oldTotal)
                .executeUpdate();
    }
}
