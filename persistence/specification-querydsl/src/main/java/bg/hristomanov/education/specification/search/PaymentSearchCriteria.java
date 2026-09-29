package bg.hristomanov.education.specification.search;

import bg.hristomanov.education.specification.domain.PaymentMethod;
import bg.hristomanov.education.specification.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentSearchCriteria(
        PaymentStatus status,
        PaymentMethod method,
        String countryCode,
        BigDecimal minimumAmount,
        LocalDateTime createdFrom,
        LocalDateTime createdTo,
        String referenceContains
) {

    public static PaymentSearchCriteria empty() {
        return new PaymentSearchCriteria(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
