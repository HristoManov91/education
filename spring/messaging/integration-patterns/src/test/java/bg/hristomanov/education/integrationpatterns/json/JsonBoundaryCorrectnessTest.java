package bg.hristomanov.education.integrationpatterns.json;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Java 25 + Jackson 3: check the actual configured parser policy.
 *
 * JEP 540 is an incubating JDK 28 API and cannot be compiled by this module.
 * This suite deliberately proves which Jackson options are required for
 * comparable strictness, rather than assuming every mapper shares defaults.
 */
class JsonBoundaryCorrectnessTest {

    private static final JsonMapper DEFAULT = JsonMapper.builder().build();
    private static final JsonMapper STRICT =
            JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    @Test
    void strictDuplicateDetectionRejectsPrivilegeOverride() {
        assertThatThrownBy(() -> STRICT.readTree(
                "{\"role\":\"USER\",\"role\":\"ADMIN\"}"))
                .isInstanceOf(StreamReadException.class);
    }

    @Test
    void strictDuplicateDetectionRejectsDuplicatesInsideNestedObjects() {
        assertThatThrownBy(() -> STRICT.readTree(
                "{\"account\":{\"enabled\":false,\"enabled\":true}}"))
                .isInstanceOf(StreamReadException.class);
    }

    @Test
    void defaultJacksonIsNotAutomaticallyEquivalentToJdk28StrictMode() {
        JsonNode parsed = DEFAULT.readTree("{\"role\":\"USER\",\"role\":\"ADMIN\"}");
        assertThat(parsed.get("role").asText()).isEqualTo("ADMIN");
        // Deliberate comparison, not endorsement: duplicate keys can change
        // authorization decisions when a different component chooses "first wins".
    }

    @Test
    void trailingCommaIsRejectedByStrictDefaultAndOptInChangesSemantics() {
        assertThatThrownBy(() -> STRICT.readTree("{\"a\":1,}"))
                .isInstanceOf(StreamReadException.class);
        JsonMapper relaxed =
                JsonMapper.builder().enable(JsonReadFeature.ALLOW_TRAILING_COMMA).build();
        assertThat(relaxed.readTree("{\"a\":1,}").get("a").intValue()).isEqualTo(1);
    }

    @Test
    void commentsAreRejectedByDefaultButCanBeAllowedWithExplicitConfig() {
        assertThatThrownBy(() -> STRICT.readTree("{/* comment */ \"a\":1}"))
                .isInstanceOf(StreamReadException.class);
        JsonMapper relaxed =
                JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build();
        assertThat(relaxed.readTree("{/* comment */ \"a\":1}").get("a").intValue()).isEqualTo(1);
    }

    @Test
    void missingAndExplicitNullHaveDifferentNodeSemantics() {
        JsonNode root = STRICT.readTree("{\"middleName\":null,\"name\":\"Ada\"}");
        assertThat(root.get("missing")).isNull();
        assertThat(root.path("missing").isMissingNode()).isTrue();
        assertThat(root.get("middleName").isNull()).isTrue();
        assertThat(root.path("name").asText()).isEqualTo("Ada");
    }

    @Test
    void strictJsonRejectsInvalidEscapesAndUnterminatedStrings() {
        assertThatThrownBy(() -> STRICT.readTree("{\"name\":\"bad\\q\"}"))
                .isInstanceOf(StreamReadException.class);
        assertThatThrownBy(() -> STRICT.readTree("{\"name\":\"unterminated}"))
                .isInstanceOf(StreamReadException.class);
    }

    @Test
    void integerValueIsExactButFloatingPointConversionCanLosePrecision() {
        JsonNode node = STRICT.readTree("9007199254740993");
        assertThat(node.longValue()).isEqualTo(9_007_199_254_740_993L);
        assertThat(node.doubleValue()).isEqualTo(9_007_199_254_740_992.0);
        assertThat(new BigInteger(node.toString()))
                .isEqualTo(new BigInteger("9007199254740993"));
    }

    @Test
    void decimalNodeCanPreserveExactMoneyValueInsteadOfConvertingToDouble() {
        // Jackson 3 defaults untyped floating-point values to double.
        // Precision is a caller policy: enable BigDecimal for tree parsing.
        JsonMapper preciseMapper = JsonMapper.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .build();
        String json = "0.123456789123456789123456789";
        BigDecimal expected = new BigDecimal(json);

        JsonNode roundedDefault = DEFAULT.readTree(json);
        assertThat(roundedDefault.decimalValue().compareTo(expected)).isNotZero();

        JsonNode exact = preciseMapper.readTree(json);
        BigDecimal precise = exact.decimalValue();
        assertThat(precise.compareTo(expected)).isZero();
        assertThat(new BigDecimal(Double.toString(precise.doubleValue())).compareTo(precise))
                .isNotZero();
    }

    @Test
    void recordDataBindingIsUsefulInSpringButIsNotJep540Functionality() {
        record Request(String orderId, BigDecimal amount) {}
        Request request = STRICT.readValue(
                "{\"orderId\":\"ORDER-42\",\"amount\":125.50}", Request.class);
        assertThat(request.orderId()).isEqualTo("ORDER-42");
        assertThat(request.amount()).isEqualByComparingTo("125.50");
    }

    @Test
    void strictParserDoesNotReplaceBusinessValidationOrJsonSchema() {
        // "Correct JSON" is not the same as "valid input for this endpoint".
        JsonNode root = STRICT.readTree("{\"amount\":-100,\"currency\":\"EUR\"}");
        assertThat(root.get("amount").longValue()).isNegative();
        assertThatThrownBy(() -> validatePositiveAmount(root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    private static void validatePositiveAmount(JsonNode root) {
        JsonNode amount = root.get("amount");
        if (amount == null || !amount.isNumber() || amount.decimalValue().signum() <= 0) {
            throw new IllegalArgumentException("Positive numeric amount required");
        }
    }
}
