/**
 * Negative source-compatibility example.
 * - Compiles with --release 25 (no preview).
 * - Existing class file executes under JDK 28 with preview enabled.
 * - Recompiling source with --release 28 --enable-preview must fail
 *   because the compact record constructor accesses this too early.
 */
public final class LegacyRecordConstructorProbe {

    record LegacySnapshot(long cents) {
        LegacySnapshot {
            String observation = this.toString(); // JDK 28 preview: illegal early-this access
            if (observation.isEmpty()) {
                throw new IllegalStateException("Unexpected empty state");
            }
        }
    }

    public static void main(String[] args) {
        LegacySnapshot snapshot = new LegacySnapshot(123);
        if (snapshot.cents() != 123L) {
            throw new AssertionError("Legacy class file changed semantics");
        }
        System.out.println("PASS: pre-28 record constructor bytecode is still runnable");
    }
}
