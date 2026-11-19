package bg.hristomanov.education.patterns.advanced.interpreter;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static bg.hristomanov.education.patterns.advanced.interpreter.RuleExpression.*;
import static org.assertj.core.api.Assertions.*;

class RuleExpressionTest {
    @Test void nestedGrammarEvaluatesBothConditions() {
        RuleExpression eligible = new And(new CountryIs("BG"), new AmountAtLeast(new BigDecimal("100")));
        assertThat(eligible.matches(new PolicyContext("BG", new BigDecimal("100")))).isTrue();
        assertThat(eligible.matches(new PolicyContext("BG", new BigDecimal("99.99")))).isFalse();
        assertThat(eligible.matches(new PolicyContext("DE", new BigDecimal("1000")))).isFalse();
    }

    @Test void compositeOrAndNotWorkWithoutStringEvaluation() {
        RuleExpression rule = new Or(new CountryIs("BG"), new Not(new CountryIs("US")));
        assertThat(rule.matches(new PolicyContext("US", BigDecimal.ONE))).isFalse();
        assertThat(rule.matches(new PolicyContext("FR", BigDecimal.ONE))).isTrue();
        assertThat(rule.matches(new PolicyContext("BG", BigDecimal.ONE))).isTrue();
    }

    @Test void rejectsInvalidAstInsteadOfDeferringNullFailure() {
        assertThatThrownBy(() -> new And(null, new CountryIs("BG")))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyContext("BG", null))
                .isInstanceOf(NullPointerException.class);
    }
}
