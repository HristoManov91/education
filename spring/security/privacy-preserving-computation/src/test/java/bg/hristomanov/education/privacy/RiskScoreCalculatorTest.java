package bg.hristomanov.education.privacy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Един и същ CSV се използва от Java и реалните OpenFHE Python tests.
 * Тестовете тук доказват expected score, а не криптографска сигурност.
 */
class RiskScoreCalculatorTest {

    @Test
    void sharedFixtureDefinesEightDeterministicOracleCases() throws IOException {
        InputStream stream = Objects.requireNonNull(
                getClass().getResourceAsStream("/risk-cases.csv"));
        int cases = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            assertThat(header).isEqualTo(
                    "case,age,total_cholesterol,hdl_cholesterol,systolic_pressure,"
                            + "hypertension,smoker,expected_score,expected_label");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = line.split(",", -1);
                assertThat(columns).hasSize(9);
                RiskInputs inputs = new RiskInputs(
                        Integer.parseInt(columns[1]), Integer.parseInt(columns[2]),
                        Integer.parseInt(columns[3]), Integer.parseInt(columns[4]),
                        Integer.parseInt(columns[5]), Integer.parseInt(columns[6]));
                long actual = RiskScoreCalculator.score(inputs);
                assertThat(actual).as(columns[0]).isEqualTo(Long.parseLong(columns[7]));
                assertThat(RiskScoreCalculator.classify(actual))
                        .as(columns[0]).isEqualTo(
                                RiskScoreCalculator.RiskLabel.valueOf(columns[8]));
                cases++;
            }
        }
        assertThat(cases).isEqualTo(8);
    }

    @Test
    void rejectsOutOfRangeIndicatorsBeforeEncryption() {
        assertThatThrownBy(() -> new RiskInputs(121, 200, 50, 120, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("age");
        assertThatThrownBy(() -> new RiskInputs(40, 200, 50, 120, 2, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hypertension");
        assertThatThrownBy(() -> new RiskInputs(40, 200, -1, 120, 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hdlCholesterol");
    }

    @Test
    void entireValidatedDomainFitsCenteredBfvPlaintextSpace() {
        assertThat(RiskScoreCalculator.worstCaseAbsoluteBound()).isEqualTo(1_855L);
        assertThat(RiskScoreCalculator.fitsCenteredBfv(1_855L)).isTrue();
        assertThat(RiskScoreCalculator.fitsCenteredBfv(32_769L)).isFalse();
        assertThat(RiskScoreCalculator.fitsCenteredBfv(-32_769L)).isFalse();
    }

    @Test
    void modularRepresentationMustBeInterpretedAsSigned() {
        long score = RiskScoreCalculator.score(new RiskInputs(0, 0, 200, 0, 0, 0));
        assertThat(score).isEqualTo(-400L);
        long residue = Math.floorMod(score, RiskScoreCalculator.PLAINTEXT_MODULUS);
        assertThat(residue).isEqualTo(65_137L);
        long restored = residue > RiskScoreCalculator.PLAINTEXT_MODULUS / 2
                ? residue - RiskScoreCalculator.PLAINTEXT_MODULUS
                : residue;
        assertThat(restored).isEqualTo(score);
    }

    @Test
    void exactlyAtClassificationThresholdMovesIntoNextBand() {
        assertThat(RiskScoreCalculator.classify(499)).isEqualTo(
                RiskScoreCalculator.RiskLabel.LOW);
        assertThat(RiskScoreCalculator.classify(500)).isEqualTo(
                RiskScoreCalculator.RiskLabel.MEDIUM);
        assertThat(RiskScoreCalculator.classify(999)).isEqualTo(
                RiskScoreCalculator.RiskLabel.MEDIUM);
        assertThat(RiskScoreCalculator.classify(1_000)).isEqualTo(
                RiskScoreCalculator.RiskLabel.HIGH);
    }
}
