import java.util.Objects;

/**
 * JDK 28 EA PREVIEW ONLY (JEP 401).
 *
 * Short + reference versus int + reference illustrate different potential
 * nullable/atomic layout sizes with compressed references. This program
 * asserts ONLY portable behavior, never the JVM's physical memory layout.
 */
public final class NullableFlatLayoutPreview {
    value record NarrowSong(String title, short durationSeconds) {
        NarrowSong {
            Objects.requireNonNull(title, "title");
            if (durationSeconds < 0) {
                throw new IllegalArgumentException("negative duration");
            }
        }
    }

    value record WideSong(String title, int durationSeconds) {
        WideSong {
            Objects.requireNonNull(title, "title");
            if (durationSeconds < 0) {
                throw new IllegalArgumentException("negative duration");
            }
        }
    }

    static final class PlaylistSlot {
        NarrowSong narrow;
        WideSong wide;
    }

    public static void main(String[] args) {
        PlaylistSlot slot = new PlaylistSlot();
        require(slot.narrow == null && slot.wide == null,
                "value object references must remain nullable");

        slot.narrow = new NarrowSong("demo", (short) 150);
        slot.wide = new WideSong("demo", 150);
        require(slot.narrow.durationSeconds() == 150, "wrong narrow duration");
        require(slot.wide.durationSeconds() == 150, "wrong wide duration");

        NarrowSong[] songs = new NarrowSong[2];
        require(songs[0] == null && songs[1] == null,
                "value object arrays must initialize to null");
        songs[0] = slot.narrow;
        require(songs[0].equals(new NarrowSong("demo", (short) 150)),
                "value equality changed");

        slot.narrow = null;
        slot.wide = null;
        require(slot.narrow == null && slot.wide == null,
                "nullable field behavior changed");

        System.out.println("PASS: nullable value classes, equality, and arrays");
        System.out.println("NOTE: no claim about actual heap flattening or speedup");
    }

    private static void require(boolean passed, String message) {
        if (!passed) {
            throw new AssertionError(message);
        }
    }
}
