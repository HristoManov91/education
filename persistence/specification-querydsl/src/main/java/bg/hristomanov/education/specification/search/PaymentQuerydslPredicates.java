package bg.hristomanov.education.specification.search;

import bg.hristomanov.education.specification.domain.PaymentMethod;
import bg.hristomanov.education.specification.domain.PaymentStatus;
import bg.hristomanov.education.specification.domain.QPaymentEntity;
import com.querydsl.core.types.dsl.BooleanExpression;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Същата Specification идея, но изразена чрез type-safe QueryDSL predicates.
 *
 * <p>Generated Q type-ът позволява compiler-ът да знае, че amount е BigDecimal,
 * createdAt е LocalDateTime и status е PaymentStatus. При rename на entity field
 * грешката излиза при compile time, вместо като string-based runtime defect.</p>
 */
public final class PaymentQuerydslPredicates {

    private static final QPaymentEntity PAYMENT = QPaymentEntity.paymentEntity;

    private PaymentQuerydslPredicates() {
    }

    public static BooleanExpression hasStatus(PaymentStatus status) {
        return status == null ? null : PAYMENT.status.eq(status);
    }

    public static BooleanExpression hasMethod(PaymentMethod method) {
        return method == null ? null : PAYMENT.method.eq(method);
    }

    public static BooleanExpression hasCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        return PAYMENT.countryCode.eq(countryCode.trim().toUpperCase());
    }

    public static BooleanExpression amountAtLeast(BigDecimal minimumAmount) {
        return minimumAmount == null ? null : PAYMENT.amount.goe(minimumAmount);
    }

    public static BooleanExpression createdOnOrAfter(LocalDateTime createdFrom) {
        return createdFrom == null ? null : PAYMENT.createdAt.goe(createdFrom);
    }

    public static BooleanExpression createdOnOrBefore(LocalDateTime createdTo) {
        return createdTo == null ? null : PAYMENT.createdAt.loe(createdTo);
    }

    public static BooleanExpression referenceContains(String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return null;
        }
        return PAYMENT.reference.containsIgnoreCase(fragment.trim());
    }
}
