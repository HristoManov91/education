package bg.hristomanov.education.dop;

import java.util.Objects;

/**
 * Data-oriented design: variants are transparent records, operations are
 * separate. No default case hides the addition of a new permitted subtype.
 *
 * This is a syntax/modeling exercise, not medical advice or a dosing guide.
 */
public final class MedicationOperations {

    private MedicationOperations() {
    }

    public static String describe(Dose dose) {
        Objects.requireNonNull(dose, "dose");
        return switch (dose) {
            case Tablets(int morning, int noon, int evening) ->
                    "tablets:" + morning + "/" + noon + "/" + evening;
            case Infusion(int millilitres, int minutes) ->
                    "infusion:" + millilitres + "ml/" + minutes + "min";
        };
    }

    public static boolean requiresManualReview(Medication medication) {
        Objects.requireNonNull(medication, "medication");
        return switch (medication.dose()) {
            case Tablets(int morning, int noon, int evening)
                    when medication.drug() == Drug.CAFFEINE && morning > 2 -> true;
            case Tablets(int morning, int noon, int evening) ->
                    Math.addExact(Math.addExact(morning, noon), evening) > 3;
            case Infusion(int millilitres, int minutes) -> minutes > 30;
        };
    }

    /** Record patterns can also deconstruct multiple levels of a known model. */
    public static String reviewLabel(Medication medication) {
        Objects.requireNonNull(medication, "medication");
        return switch (medication) {
            case Medication(Drug drug, Tablets(int morning, int noon, int evening)) ->
                    drug + ":" + Math.addExact(Math.addExact(morning, noon), evening) + "-tablets";
            case Medication(Drug drug, Infusion(int millilitres, int minutes)) ->
                    drug + ":" + millilitres + "ml-infusion";
        };
    }
}
