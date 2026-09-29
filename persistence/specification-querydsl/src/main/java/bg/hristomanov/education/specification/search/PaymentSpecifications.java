package bg.hristomanov.education.specification.search;

import bg.hristomanov.education.specification.domain.PaymentEntity;
import bg.hristomanov.education.specification.domain.PaymentMethod;
import bg.hristomanov.education.specification.domain.PaymentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Reusable Specification predicates.
 *
 * <p>Тук pattern-ът е по-важен от конкретното JPA API: всяко правило е малък,
 * named predicate, който може да се комбинира с други правила, вместо всеки
 * search use case да изгражда огромен if/switch блок.</p>
 */
public final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    public static Specification<PaymentEntity> matches(PaymentSearchCriteria criteria) {
        return Specification.allOf(
                hasStatus(criteria.status()),
                hasMethod(criteria.method()),
                hasCountry(criteria.countryCode()),
                amountAtLeast(criteria.minimumAmount()),
                createdOnOrAfter(criteria.createdFrom()),
                createdOnOrBefore(criteria.createdTo()),
                referenceContains(criteria.referenceContains())
        );
    }

    public static Specification<PaymentEntity> hasStatus(PaymentStatus status) {
        if (status == null) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    public static Specification<PaymentEntity> hasMethod(PaymentMethod method) {
        if (method == null) {
            return Specification.unrestricted();
        }
        return (root, query, builder) -> builder.equal(root.get("method"), method);
    }

    public static Specification<PaymentEntity> hasCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return Specification.unrestricted();
        }

        String normalized = countryCode.trim().toUpperCase();
        return (root, query, builder) -> builder.equal(root.get("countryCode"), normalized);
    }

    public static Specification<PaymentEntity> amountAtLeast(BigDecimal minimumAmount) {
        if (minimumAmount == null) {
            return Specification.unrestricted();
        }
        return (root, query, builder) ->
                builder.greaterThanOrEqualTo(root.<BigDecimal>get("amount"), minimumAmount);
    }

    public static Specification<PaymentEntity> createdOnOrAfter(LocalDateTime createdFrom) {
        if (createdFrom == null) {
            return Specification.unrestricted();
        }
        return (root, query, builder) ->
                builder.greaterThanOrEqualTo(root.<LocalDateTime>get("createdAt"), createdFrom);
    }

    public static Specification<PaymentEntity> createdOnOrBefore(LocalDateTime createdTo) {
        if (createdTo == null) {
            return Specification.unrestricted();
        }
        return (root, query, builder) ->
                builder.lessThanOrEqualTo(root.get("createdAt"), createdTo);
    }

    public static Specification<PaymentEntity> referenceContains(String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return Specification.unrestricted();
        }

        String pattern = "%" + fragment.trim().toLowerCase() + "%";
        return (root, query, builder) ->
                builder.like(builder.lower(root.get("reference")), pattern);
    }
}
