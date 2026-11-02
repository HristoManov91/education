import java.util.Objects;

/**
 * JDK 28 EA / JEP 401 preview syntax.
 * NOT compiled by the Java 25 Maven reactor.
 */
value record ValueMoney(long cents) {
    ValueMoney {
        if (cents < 0) {
            throw new IllegalArgumentException("cents must be nonnegative");
        }
    }
}

value record ValuePrice(long cents, String currency) {
    ValuePrice {
        Objects.requireNonNull(currency, "currency");
    }
}

public final class ValueObjectsPreview {

    public static void main(String[] args) {
        ValueMoney first = new ValueMoney(500);
        ValueMoney second = new ValueMoney(500);
        ValueMoney third = new ValueMoney(700);

        require(first == second, "Same statewise value objects should be ==");
        require(first.equals(second), "Record equals must compare components");
        require(first != third, "Different primitive state must not match");

        // Statewise equivalence uses == recursively for reference-typed fields;
        // record.equals uses Objects.equals (String value equality).
        ValuePrice priceA = new ValuePrice(500, new String("EUR"));
        ValuePrice priceB = new ValuePrice(500, new String("EUR"));
        require(priceA.equals(priceB), "Record equals compares String content");
        require(priceA != priceB, "Statewise == uses identity for String fields");

        System.out.println("PASS: JDK 28 value records / statewise == / record.equals");
    }

    private static void require(boolean correct, String reason) {
        if (!correct) {
            throw new AssertionError(reason);
        }
    }
}
