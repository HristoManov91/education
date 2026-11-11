package edu.upgrade;

/**
 * Compile-only demonstration of a JDK API deprecated since Java 9.
 * Do not copy into production. Java 25 may still provide this constructor;
 * deprecated does NOT necessarily mean already removed or forRemoval=true.
 */
public final class LegacyBoxing {
    private LegacyBoxing() {
    }

    public static int value() {
        // Expected deprecated API usage, intentionally not suppressed.
        return new Integer(42).intValue();
    }

    public static void main(String[] args) {
        System.out.println("VALUE=" + value());
    }
}
