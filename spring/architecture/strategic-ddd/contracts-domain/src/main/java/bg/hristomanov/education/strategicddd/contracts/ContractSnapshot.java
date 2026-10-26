package bg.hristomanov.education.strategicddd.contracts;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Contract-owned published/read model. The Claims context does not depend
 * directly on this record or its enum; an ACL translates its relevant facts.
 */
public record ContractSnapshot(
        String agreementId,
        RegisteredVehicle registeredVehicle,
        ContractStatus status,
        long revision,
        String customerSegment,
        BigDecimal agreedMonthlyEur) {

    public ContractSnapshot {
        Objects.requireNonNull(agreementId, "agreementId");
        Objects.requireNonNull(registeredVehicle, "registeredVehicle");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(customerSegment, "customerSegment");
        Objects.requireNonNull(agreedMonthlyEur, "agreedMonthlyEur");
        if (agreementId.isBlank() || customerSegment.isBlank() || revision < 1
                || agreedMonthlyEur.signum() < 0) {
            throw new IllegalArgumentException("Invalid published contract facts");
        }
    }
}
