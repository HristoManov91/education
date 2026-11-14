import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import jdk.incubator.json.Json;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonParseException;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;
import jdk.incubator.json.JsonValueException;

/**
 * A test-by-execution micro lab for JEP 540, separate from Java 25 Maven.
 *
 * Compiles ONLY on the actual OpenJDK 28 EA incubator module. Every check
 * is a throw-on-failure assertion: this class never skips negative paths.
 */
public final class Jdk28SimpleJsonLab {

    private static int passed;

    private Jdk28SimpleJsonLab() {}

    public static void main(String[] args) {
        run("required configuration and optional default", () -> {
            JsonValue root = Json.parse("""
                {"charset":"UTF-8", "port":8081}
                """);
            check("UTF-8".equals(root.get("charset").asString()), "charset");
            check(root.tryGet("port").map(JsonValue::asInt).orElse(8080) == 8081, "port");
            JsonValue withoutPort = Json.parse("{\"charset\":\"UTF-8\"}");
            check(withoutPort.tryGet("port").map(JsonValue::asInt).orElse(8080) == 8080, "default port");
            expect(JsonValueException.class, () -> withoutPort.get("port"));
        });

        run("absent and explicit JSON null are not the same", () -> {
            JsonValue root = Json.parse("{\"optional\":null,\"name\":\"Ada\"}");
            check(root.tryGet("missing").isEmpty(), "missing optional");
            check(root.tryGet("optional").isPresent(), "explicit null must be present");
            check(root.get("optional").tryValue().isEmpty(), "tryValue excludes explicit JsonNull");
            check(root.get("name").tryValue().isPresent(), "non-null value");
        });

        run("duplicate property names are rejected, including nested", () -> {
            expect(JsonParseException.class, () -> Json.parse("{\"role\":\"USER\",\"role\":\"ADMIN\"}"));
            expect(JsonParseException.class, () -> Json.parse("{\"nested\":{\"id\":1,\"id\":2}}"));
        });

        run("comments and trailing commas are not RFC 8259 JSON", () -> {
            expect(JsonParseException.class, () -> Json.parse("{/*comment*/ \"ok\":true}"));
            expect(JsonParseException.class, () -> Json.parse("{\"ok\":true,}"));
            expect(JsonParseException.class, () -> Json.parse("[true,]"));
        });

        run("invalid JSON escapes are rejected", () -> {
            expect(JsonParseException.class, () -> Json.parse("{\"name\":\"bad\\q\"}"));
            expect(JsonParseException.class, () -> Json.parse("{\"name\":\"unterminated}"));
        });

        run("numeric exactness is enforced for int and long", () -> {
            check(Json.parse("7.0").asInt() == 7, "exact integral decimal");
            expect(JsonValueException.class, () -> Json.parse("1.25").asInt());
            expect(JsonValueException.class, () -> Json.parse("2147483648").asInt());
            expect(JsonValueException.class, () -> Json.parse("9223372036854775808").asLong());
            check(Json.parse("9007199254740993").asLong() == 9_007_199_254_740_993L,
                    "exact 2^53 + 1 long");
        });

        run("asDouble is not promised to preserve decimal exactness", () -> {
            JsonValue number = Json.parse("9007199254740993");
            check(number.asDouble() == 9_007_199_254_740_992.0, "rounded beyond 2^53");
            expect(JsonValueException.class, () -> Json.parse("1e400").asDouble());
        });

        run("BigDecimal and BigInteger preserve arbitrary precision", () -> {
            JsonValue number = Json.parse("0.123456789123456789123456789");
            BigDecimal decimal = new BigDecimal(number.toString());
            check(decimal.compareTo(new BigDecimal("0.123456789123456789123456789")) == 0,
                    "exact arbitrary scale");
            JsonValue integer = Json.parse("123456789012345678901234567890");
            BigInteger value = new BigInteger(integer.toString());
            check(value.equals(new BigInteger("123456789012345678901234567890")), "arbitrary integer");
        });

        run("wrong type, missing key and wrong index fail explicitly", () -> {
            JsonValue root = Json.parse("{\"id\":\"17\",\"values\":[1]}");
            expect(JsonValueException.class, () -> root.get("id").asInt());
            expect(JsonValueException.class, () -> root.get("unknown"));
            expect(JsonValueException.class, () -> root.get("values").get(2));
            expect(JsonValueException.class, () -> root.asString());
        });

        run("JSON tree collections are immutable", () -> {
            JsonValue root = Json.parse("{\"values\":[1,2],\"name\":\"Ada\"}");
            expect(UnsupportedOperationException.class,
                    () -> root.asMap().put("evil", JsonString.of("injected")));
            expect(UnsupportedOperationException.class,
                    () -> root.get("values").asList().add(JsonNumber.of(3)));
        });

        run("parsing char arrays does not retain mutable input buffers", () -> {
            char[] raw = "{\"name\":\"Ada\"}".toCharArray();
            JsonValue root = Json.parse(raw);
            raw[9] = 'Z';
            check("Ada".equals(root.get("name").asString()), "original JSON parse retained");
        });

        run("JSON generation and pretty display round-trip", () -> {
            JsonObject object = JsonObject.of(Map.of(
                    "status", JsonString.of("ok"), "count", JsonNumber.of(3)));
            JsonValue roundTrip = Json.parse(object.toString());
            check("ok".equals(roundTrip.get("status").asString()), "roundtrip string");
            check(roundTrip.get("count").asInt() == 3, "roundtrip number");
            String pretty = Json.toDisplayString(object, "  ");
            check(pretty.contains("\n"), "pretty JSON must contain a newline");
            check(Json.parse(pretty).get("count").asInt() == 3, "pretty roundtrip");
        });

        run("parse preserves member iteration order but business must not depend on it", () -> {
            JsonValue root = Json.parse("{\"second\":2,\"first\":1}");
            check(List.copyOf(root.asMap().keySet()).equals(List.of("second", "first")),
                    "encounter order in JDK 28 Json.parse");
            check(root.get("first").asInt() == 1, "access by member name");
        });

        run("compatibility across string and numeric identifiers is explicit", () -> {
            check(readId(Json.parse("{\"id\":\"123\"}")) == 123L, "legacy quoted id");
            check(readId(Json.parse("{\"id\":123}")) == 123L, "new numeric id");
            expect(NumberFormatException.class, () -> readId(Json.parse("{\"id\":\"abc\"}")));
            expect(IllegalArgumentException.class, () -> readId(Json.parse("{\"id\":null}")));
        });

        System.out.println("PASS JDK28_JSON_API: " + passed + " positive/negative cases");
    }

    private static long readId(JsonValue root) {
        JsonValue value = root.get("id");
        if (value instanceof JsonNumber number) {
            return number.asLong();
        }
        if (value instanceof JsonString text) {
            return Long.parseLong(text.asString());
        }
        throw new IllegalArgumentException("Unsupported id type");
    }

    private static void run(String label, Runnable test) {
        test.run();
        passed++;
        System.out.println("PASS " + passed + ": " + label);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expect(Class<? extends Throwable> kind, Runnable action) {
        try {
            action.run();
        } catch (Throwable caught) {
            if (kind.isInstance(caught)) {
                return;
            }
            throw new AssertionError("Expected " + kind.getSimpleName() + " but got " + caught, caught);
        }
        throw new AssertionError("Expected " + kind.getSimpleName() + ", but nothing was thrown");
    }
}
