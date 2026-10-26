package bg.hristomanov.education.dop;

/** One tablet schedule; invalid/overflowing counts are rejected at construction. */
public record Tablets(int morning, int noon, int evening) implements Dose {

    public Tablets {
        if (morning < 0 || noon < 0 || evening < 0) {
            throw new IllegalArgumentException("Tablet counts cannot be negative");
        }
        int total = Math.addExact(Math.addExact(morning, noon), evening);
        if (total == 0) {
            throw new IllegalArgumentException("At least one tablet is required");
        }
    }
}
