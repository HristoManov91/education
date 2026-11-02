import java.lang.ref.WeakReference;
import java.time.LocalDate;

/**
 * Java 25-compatible source: the same bytecode is run with JDK 28 EA
 * both with and without preview. No dependency on preview API at compile time.
 */
public final class IdentityPreviewProbe {

    public static void main(String[] args) {
        if (args.length != 1 || !(args[0].equals("identity") || args[0].equals("value"))) {
            throw new IllegalArgumentException("Expected identity or value");
        }
        boolean preview = args[0].equals("value");
        LocalDate first = LocalDate.of(2026, 7, 31);
        LocalDate second = LocalDate.of(2026, 7, 31);

        require(first.equals(second), "Business equality must remain true");
        require((first == second) == preview,
                "LocalDate == must reflect the JDK 28 preview mode");

        try {
            WeakReference<LocalDate> reference = new WeakReference<>(first);
            require(!preview && reference.get() == first,
                    "WeakReference must work only with identity objects");
        } catch (RuntimeException exception) {
            require(preview && exception.getClass().getName().equals("java.lang.IdentityException"),
                    "Expected IdentityException with preview, got " + exception);
        }

        System.out.println("PASS: JDK 28 " + (preview ? "preview ON" : "preview OFF")
                + " equality / WeakReference behavior");
    }

    private static void require(boolean correct, String reason) {
        if (!correct) {
            throw new AssertionError(reason);
        }
    }
}
