package bg.hristomanov.education.specification.repository;

import bg.hristomanov.education.specification.domain.PaymentEntity;
import bg.hristomanov.education.specification.search.PaymentSearchCriteria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Валиден, но трудно reusable naive вариант.
 *
 * <p>Проблемът не е Criteria API-то. Проблемът е, че всички правила са inline
 * в един query method. Когато друг use case поиска "paid + BG" или
 * "amount >= X", започваме да копираме predicate construction logic.</p>
 */
@Repository
public class NaivePaymentCriteriaRepository {

    private final EntityManager entityManager;

    public NaivePaymentCriteriaRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<PaymentEntity> search(PaymentSearchCriteria criteria) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<PaymentEntity> query = builder.createQuery(PaymentEntity.class);
        Root<PaymentEntity> payment = query.from(PaymentEntity.class);
        List<Predicate> predicates = new ArrayList<>();

        if (criteria.status() != null) {
            predicates.add(builder.equal(payment.get("status"), criteria.status()));
        }
        if (criteria.method() != null) {
            predicates.add(builder.equal(payment.get("method"), criteria.method()));
        }
        if (criteria.countryCode() != null && !criteria.countryCode().isBlank()) {
            predicates.add(
                    builder.equal(
                            payment.get("countryCode"),
                            criteria.countryCode().trim().toUpperCase()
                    )
            );
        }
        if (criteria.minimumAmount() != null) {
            predicates.add(
                    builder.greaterThanOrEqualTo(
                            payment.get("amount"),
                            criteria.minimumAmount()
                    )
            );
        }
        if (criteria.createdFrom() != null) {
            predicates.add(
                    builder.greaterThanOrEqualTo(
                            payment.get("createdAt"),
                            criteria.createdFrom()
                    )
            );
        }
        if (criteria.createdTo() != null) {
            predicates.add(
                    builder.lessThanOrEqualTo(
                            payment.get("createdAt"),
                            criteria.createdTo()
                    )
            );
        }
        if (criteria.referenceContains() != null && !criteria.referenceContains().isBlank()) {
            predicates.add(
                    builder.like(
                            builder.lower(payment.get("reference")),
                            "%" + criteria.referenceContains().trim().toLowerCase() + "%"
                    )
            );
        }

        query.select(payment)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(builder.desc(payment.get("createdAt")), builder.asc(payment.get("id")));

        return entityManager.createQuery(query).getResultList();
    }
}
