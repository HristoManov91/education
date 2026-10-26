package bg.hristomanov.education.dop;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DataOrientedProgrammingTest {

    @Test
    void bothPermittedVariantsHaveExhaustivelyHandledDescriptions() {
        assertThat(MedicationOperations.describe(new Tablets(1, 0, 2)))
                .isEqualTo("tablets:1/0/2");
        assertThat(MedicationOperations.describe(new Infusion(50, 20)))
                .isEqualTo("infusion:50ml/20min");
    }

    @Test
    void guardedPatternDoesNotHideUnguardedVariant() {
        assertThat(MedicationOperations.requiresManualReview(
                new Medication(Drug.CAFFEINE, new Tablets(3, 0, 0)))).isTrue();
        assertThat(MedicationOperations.requiresManualReview(
                new Medication(Drug.PAIN_RELIEF, new Tablets(3, 0, 0)))).isFalse();
        assertThat(MedicationOperations.requiresManualReview(
                new Medication(Drug.PAIN_RELIEF, new Tablets(2, 2, 0)))).isTrue();
        assertThat(MedicationOperations.requiresManualReview(
                new Medication(Drug.CAFFEINE, new Infusion(50, 31)))).isTrue();
    }

    @Test
    void nestedRecordPatternDeconstructsMedicationAndDosage() {
        assertThat(MedicationOperations.reviewLabel(
                new Medication(Drug.CAFFEINE, new Tablets(1, 1, 0))))
                .isEqualTo("CAFFEINE:2-tablets");
        assertThat(MedicationOperations.reviewLabel(
                new Medication(Drug.PAIN_RELIEF, new Infusion(100, 20))))
                .isEqualTo("PAIN_RELIEF:100ml-infusion");
    }

    @Test
    void compactConstructorsRejectInvalidStatesAtBoundary() {
        assertThatThrownBy(() -> new Tablets(-1, 0, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Tablets(0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Infusion(0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Medication(null, new Tablets(1, 0, 0)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void arithmeticOverflowCannotMakeAnInvalidScheduleLookValid() {
        assertThatThrownBy(() -> new Tablets(Integer.MAX_VALUE, 1, 0))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void recordIsShallowlyImmutableUnlessNestedCollectionsAreCopied() {
        List<Medication> input = new ArrayList<>();
        input.add(new Medication(Drug.CAFFEINE, new Tablets(1, 0, 0)));
        Prescription prescription = new Prescription("patient-1", input);

        input.add(new Medication(Drug.PAIN_RELIEF, new Infusion(10, 5)));
        assertThat(prescription.medications()).hasSize(1);
        assertThatThrownBy(() -> prescription.medications().add(
                new Medication(Drug.CAFFEINE, new Tablets(1, 0, 0))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void markdownJavaDocExampleMatchesExecutedCode() {
        assertThat(DocumentationExample.sampleDescription())
                .isEqualTo("tablets:1/0/1");
    }

    @Test
    void structuralRecordEqualityUsesComponents() {
        assertThat(new Medication(Drug.CAFFEINE, new Tablets(1, 0, 0)))
                .isEqualTo(new Medication(Drug.CAFFEINE, new Tablets(1, 0, 0)));
    }
}
