import java.lang.reflect.Field;

/**
 * Java 25/26 migration laboratory for JEP 500; no third-party dependencies.
 * Reflectively changing a private final field breaks encapsulation.
 * Read through Field#getInt, not a potentially JIT-inlined accessor.
 * Intentionally unsafe: never copy into production code.
 */
public final class FinalFieldMutationDemo {
    private FinalFieldMutationDemo() {}

    private static final class Policy {
        private final int ageLimit;

        Policy(int ageLimit) {
            if (ageLimit < 18) {
                throw new IllegalArgumentException("ageLimit must be at least 18");
            }
            this.ageLimit = ageLimit;
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Policy policy = new Policy(21);
        Field field = Policy.class.getDeclaredField("ageLimit");
        field.setAccessible(true);

        try {
            field.setInt(policy, 17);
            if (field.getInt(policy) != 17) {
                throw new AssertionError("Expected reflective write to take effect");
            }
            System.out.println("MUTATION_ALLOWED ageLimit=17");
        } catch (IllegalAccessException denied) {
            if (field.getInt(policy) != 21) {
                throw new AssertionError("Denied mutation must preserve original state");
            }
            System.out.println("MUTATION_DENIED IllegalAccessException ageLimit=21");
        }
    }
}
