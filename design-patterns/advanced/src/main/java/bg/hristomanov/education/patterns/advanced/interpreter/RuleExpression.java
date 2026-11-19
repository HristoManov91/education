package bg.hristomanov.education.patterns.advanced.interpreter;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Interpreter: a tiny, deliberately explicit AST. This is NOT a parser and never evaluates
 * user-supplied Java/SQL strings. A full expression language needs grammar, bounds and validation.
 */
public sealed interface RuleExpression
        permits RuleExpression.CountryIs, RuleExpression.AmountAtLeast,
                RuleExpression.And, RuleExpression.Or, RuleExpression.Not {

    boolean matches(PolicyContext context);

    record PolicyContext(String country, BigDecimal amount) {
        public PolicyContext {
            Objects.requireNonNull(country, "country");
            Objects.requireNonNull(amount, "amount");
        }
    }

    record CountryIs(String expected) implements RuleExpression {
        public CountryIs {
            Objects.requireNonNull(expected, "expected");
        }
        @Override public boolean matches(PolicyContext context) {
            return expected.equals(Objects.requireNonNull(context, "context").country());
        }
    }

    record AmountAtLeast(BigDecimal minimum) implements RuleExpression {
        public AmountAtLeast {
            Objects.requireNonNull(minimum, "minimum");
        }
        @Override public boolean matches(PolicyContext context) {
            return Objects.requireNonNull(context, "context").amount().compareTo(minimum) >= 0;
        }
    }

    record And(RuleExpression left, RuleExpression right) implements RuleExpression {
        public And {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }
        @Override public boolean matches(PolicyContext context) {
            return left.matches(context) && right.matches(context);
        }
    }

    record Or(RuleExpression left, RuleExpression right) implements RuleExpression {
        public Or {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }
        @Override public boolean matches(PolicyContext context) {
            return left.matches(context) || right.matches(context);
        }
    }

    record Not(RuleExpression expression) implements RuleExpression {
        public Not {
            Objects.requireNonNull(expression, "expression");
        }
        @Override public boolean matches(PolicyContext context) {
            return !expression.matches(context);
        }
    }
}
