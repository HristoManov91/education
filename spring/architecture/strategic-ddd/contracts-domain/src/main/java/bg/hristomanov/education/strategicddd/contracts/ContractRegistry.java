package bg.hristomanov.education.strategicddd.contracts;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Educational contract owner with atomic per-key operations and revision numbers.
 * In-memory storage is NOT an authoritative durable database and is not shared
 * between application instances.
 */
public final class ContractRegistry {

    private final ConcurrentMap<String, ContractSnapshot> contracts = new ConcurrentHashMap<>();

    public ContractSnapshot activate(String agreementId, RegisteredVehicle vehicle,
                                      String customerSegment, BigDecimal monthlyEur) {
        ContractSnapshot initial = new ContractSnapshot(
                agreementId, vehicle, ContractStatus.ACTIVE, 1L, customerSegment, monthlyEur);
        if (contracts.putIfAbsent(agreementId, initial) != null) {
            throw new IllegalStateException("Contract already exists: " + agreementId);
        }
        return initial;
    }

    public ContractSnapshot terminate(String agreementId) {
        return updateExisting(agreementId, existing -> existing.status() == ContractStatus.TERMINATED
                ? existing
                : new ContractSnapshot(existing.agreementId(), existing.registeredVehicle(),
                        ContractStatus.TERMINATED, existing.revision() + 1,
                        existing.customerSegment(), existing.agreedMonthlyEur()));
    }

    public ContractSnapshot changeRegistrationPlate(String agreementId, String newPlate) {
        return updateExisting(agreementId, existing -> {
            RegisteredVehicle oldVehicle = existing.registeredVehicle();
            RegisteredVehicle updatedVehicle = new RegisteredVehicle(
                    oldVehicle.vin(), newPlate, oldVehicle.modelName());
            return new ContractSnapshot(existing.agreementId(), updatedVehicle,
                    existing.status(), existing.revision() + 1,
                    existing.customerSegment(), existing.agreedMonthlyEur());
        });
    }

    public Optional<ContractSnapshot> find(String agreementId) {
        return Optional.ofNullable(contracts.get(agreementId));
    }

    private ContractSnapshot updateExisting(String agreementId,
                                             java.util.function.UnaryOperator<ContractSnapshot> change) {
        if (agreementId == null || agreementId.isBlank()) {
            throw new IllegalArgumentException("agreementId required");
        }
        ContractSnapshot result = contracts.computeIfPresent(agreementId,
                (id, existing) -> change.apply(existing));
        if (result == null) {
            throw new IllegalArgumentException("Unknown agreement: " + agreementId);
        }
        return result;
    }
}
