import java.time.LocalDate;
import java.util.Objects;

/**
 * Compile this exact Java 25 compatible source twice:
 * --release 25 and --release 28 --enable-preview. Check actual classfile
 * metadata, while keeping the business contract unchanged.
 */
public final class LoadableDescriptorsProbe {
    static final class Schedule {
        final LocalDate day;

        Schedule(LocalDate day) {
            this.day = Objects.requireNonNull(day, "day");
        }

        String label() {
            return day.toString();
        }
    }

    public static void main(String[] args) {
        Schedule first = new Schedule(LocalDate.of(2026, 11, 18));
        Schedule second = new Schedule(LocalDate.of(2026, 11, 18));

        require(first.label().equals("2026-11-18"), "wrong representation");
        require(first.day.equals(second.day), "logical equality changed");
        require(!first.day.equals(LocalDate.of(2026, 11, 19)), "distinct dates collapsed");
        System.out.println("PASS: identical business behavior in old and new class files");
    }

    private static void require(boolean passed, String message) {
        if (!passed) {
            throw new AssertionError(message);
        }
    }
}
