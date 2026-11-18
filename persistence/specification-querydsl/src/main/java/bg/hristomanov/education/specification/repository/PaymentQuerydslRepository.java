package bg.hristomanov.education.specification.repository;

import bg.hristomanov.education.specification.domain.PaymentEntity;
import bg.hristomanov.education.specification.domain.QPaymentEntity;
import bg.hristomanov.education.specification.search.PaymentQuerydslPredicates;
import bg.hristomanov.education.specification.search.PaymentSearchCriteria;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;

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
        BooleanBuilder predicate = predicate(criteria);

        return queryFactory
                .selectFrom(PAYMENT)
                .where(predicate)
                .orderBy(PAYMENT.createdAt.desc(), PAYMENT.id.asc())
                .fetch();
    }


    private static final Sort FIXED_ORDER = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    /** Explicit two-query Page: one bounded data query, one count query. */
    public Page<PaymentEntity> page(PaymentSearchCriteria criteria, int pageNumber, int pageSize) {
        var pageable = boundedPage(pageNumber, pageSize);
        var predicate = predicate(criteria);
        var content = queryFactory
                .selectFrom(PAYMENT)
                .where(predicate)
                .orderBy(PAYMENT.createdAt.desc(), PAYMENT.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        Long total = queryFactory.select(PAYMENT.id.count())
                .from(PAYMENT).where(predicate).fetchOne();
        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    /** Bounded fetch(size + 1); no COUNT SQL when total pages are not needed. */
    public Slice<PaymentEntity> slice(PaymentSearchCriteria criteria, int pageNumber, int pageSize) {
        var pageable = boundedPage(pageNumber, pageSize);
        var contentPlusOne = queryFactory.selectFrom(PAYMENT)
                .where(predicate(criteria))
                .orderBy(PAYMENT.createdAt.desc(), PAYMENT.id.asc())
                .offset(pageable.getOffset())
                .limit((long) pageSize + 1)
                .fetch();
        boolean hasNext = contentPlusOne.size() > pageSize;
        var content = hasNext ? contentPlusOne.subList(0, pageSize) : contentPlusOne;
        return new SliceImpl<>(content, pageable, hasNext);
    }

    private static PageRequest boundedPage(int pageNumber, int pageSize) {
        if (pageNumber < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageNumber >= 0, pageSize 1..100 required");
        }
        return PageRequest.of(pageNumber, pageSize, FIXED_ORDER);
    }

    private BooleanBuilder predicate(PaymentSearchCriteria criteria) {
        BooleanBuilder predicate = new BooleanBuilder();
        andIfPresent(predicate, PaymentQuerydslPredicates.hasStatus(criteria.status()));
        andIfPresent(predicate, PaymentQuerydslPredicates.hasMethod(criteria.method()));
        andIfPresent(predicate, PaymentQuerydslPredicates.hasCountry(criteria.countryCode()));
        andIfPresent(predicate, PaymentQuerydslPredicates.amountAtLeast(criteria.minimumAmount()));
        andIfPresent(predicate, PaymentQuerydslPredicates.createdOnOrAfter(criteria.createdFrom()));
        andIfPresent(predicate, PaymentQuerydslPredicates.createdOnOrBefore(criteria.createdTo()));
        andIfPresent(predicate, PaymentQuerydslPredicates.referenceContains(criteria.referenceContains()));
        return predicate;
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
