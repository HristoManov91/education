package bg.hristomanov.education.strategicddd.contracts;

import java.util.Objects;

/**
 * "Vehicle" in Contracts means a concrete physical car with VIN and registration.
 * This cannot be substituted with Quoting's configurable vehicle model.
 */
public record RegisteredVehicle(String vin, String registrationPlate, String modelName) {

    public RegisteredVehicle {
        Objects.requireNonNull(vin, "vin");
        Objects.requireNonNull(registrationPlate, "registrationPlate");
        Objects.requireNonNull(modelName, "modelName");

        // Illustrative validation only: complete VIN verification also needs check
        // digit/manufacturer-specific rules and is outside this DDD exercise.
        if (!vin.matches("[A-HJ-NPR-Z0-9]{17}")) {
            throw new IllegalArgumentException("VIN must use 17 valid uppercase characters");
        }
        if (registrationPlate.isBlank() || modelName.isBlank()) {
            throw new IllegalArgumentException("Registration plate and model name are required");
        }
    }
}
