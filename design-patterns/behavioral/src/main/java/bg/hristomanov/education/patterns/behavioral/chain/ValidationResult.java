package bg.hristomanov.education.patterns.behavioral.chain;

public record ValidationResult(
        boolean valid,
        String error
) {

    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult failure(String error) {
        return new ValidationResult(false, error);
    }
}
