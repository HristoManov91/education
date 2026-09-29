package bg.hristomanov.education.specification.repository;

import bg.hristomanov.education.specification.domain.PaymentEntity;
import bg.hristomanov.education.specification.domain.QPaymentEntity;
import bg.hristomanov.education.specification.search.PaymentQuerydslPredicates;
import bg.hristomanov.education.specification.search.PaymentSearchCriteria;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * QueryDSL implementation на dynamic search.
 *
 * <p>Repository-то orchestratе-ва query shape-а, докато отделните predicates
 * са reusable и domain-named. Така query construction не се превръща в един
 * гигантски method с всички business правила inline.</p>
 */
@Repository
public class PaymentQuerydslRepository {

    private static final QPaymentEntity PAYMENT = QPaymentEntity.paymentEntity;

    private final JPAQueryFactory queryFactory;

    public PaymentQuerydslRepository(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    public List<PaymentEntity> search(PaymentSearchCriteria criteria) {
        BooleanBuilder predicate = new BooleanBuilder();

        andIfPresent(predicate, PaymentQuerydslPredicates.hasStatus(criteria.status()));
        andIfPresent(predicate, PaymentQuerydslPredicates.hasMethod(criteria.method()));
        andIfPresent(predicate, PaymentQuerydslPredicates.hasCountry(criteria.countryCode()));
        andIfPresent(predicate, PaymentQuerydslPredicates.amountAtLeast(criteria.minimumAmount()));
        andIfPresent(predicate, PaymentQuerydslPredicates.createdOnOrAfter(criteria.createdFrom()));
        andIfPresent(predicate, PaymentQuerydslPredicates.createdOnOrBefore(criteria.createdTo()));
        andIfPresent(predicate, PaymentQuerydslPredicates.referenceContains(criteria.referenceContains()));

        return queryFactory
                .selectFrom(PAYMENT)
                .where(predicate)
                .orderBy(PAYMENT.createdAt.desc(), PAYMENT.id.asc())
                .fetch();
    }

    private void andIfPresent(
            BooleanBuilder builder,
            com.querydsl.core.types.dsl.BooleanExpression expression
    ) {
        if (expression != null) {
            builder.and(expression);
        }
    }
}
