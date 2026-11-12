package bg.hristomanov.education.privacy;

/**
 * Независим plaintext test oracle за FHE лабораторията.
 *
 * Скалата е синтетична. Изчислението е в signed integers; BFV връща
 * числа по модул t, затова преди encryption доказваме, че резултатите
 * се събират в центрирания интервал [-t/2, t/2]. Java Math.*Exact
 * не позволява silently wrapping при бъдещи промени на бизнес модела.
 */
public final class RiskScoreCalculator {

    public static final int PLAINTEXT_MODULUS = 65_537;
    public static final String MODEL_VERSION = "synthetic-risk-v1";
    private static final int[] WEIGHTS = {3, 1, -2, 2, 75, 120};
    private static final int[] MAX_INPUTS = {120, 400, 200, 250, 1, 1};

    private RiskScoreCalculator() {
    }

    public enum RiskLabel {
        LOW, MEDIUM, HIGH
    }

    public static long score(RiskInputs values) {
        int[] inputs = values.asVector();
        long total = 0L;
        for (int i = 0; i < inputs.length; i++) {
            long product = Math.multiplyExact((long) inputs[i], WEIGHTS[i]);
            total = Math.addExact(total, product);
        }
        if (!fitsCenteredBfv(total)) {
            throw new IllegalArgumentException("Score is outside signed BFV plaintext range");
        }
        return total;
    }

    public static RiskLabel classify(long score) {
        if (score < 500L) {
            return RiskLabel.LOW;
        }
        if (score < 1_000L) {
            return RiskLabel.MEDIUM;
        }
        return RiskLabel.HIGH;
    }

    public static boolean fitsCenteredBfv(long score) {
        long half = (PLAINTEXT_MODULUS - 1L) / 2L;
        return score >= -half && score <= half;
    }

    public static long worstCaseAbsoluteBound() {
        long maxAbs = 0L;
        for (int i = 0; i < WEIGHTS.length; i++) {
            maxAbs = Math.addExact(maxAbs,
                    Math.multiplyExact(Math.abs((long) WEIGHTS[i]), MAX_INPUTS[i]));
        }
        return maxAbs;
    }
}
