package bg.hristomanov.education.strategicddd.quoting;

import java.util.Objects;
import java.util.Set;

/**
 * "Vehicle" in Quoting means a configurable model, not a registered physical asset.
 * VIN and registration plate are intentionally absent from this bounded context.
 */
public record VehicleConfiguration(String modelCode, Set<String> optionCodes) {

    public VehicleConfiguration {
        Objects.requireNonNull(modelCode, "modelCode");
        Objects.requireNonNull(optionCodes, "optionCodes");
        if (modelCode.isBlank() || optionCodes.stream().anyMatch(
                code -> code == null || code.isBlank())) {
            throw new IllegalArgumentException("Invalid vehicle configuration");
        }
        optionCodes = Set.copyOf(optionCodes);
    }
}
