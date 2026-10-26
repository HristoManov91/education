package bg.hristomanov.education.performance;

import java.util.ArrayList;
import java.util.List;

/**
 * Small, deterministic class-loading/allocation exercise for separate JVM
 * process AOT experiments. Not a representative enterprise benchmark.
 */
public final class StartupWorkload {

    private record Sample(int index, String label) {
    }

    private StartupWorkload() {
    }

    public static long checksum(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
        List<Sample> samples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            samples.add(new Sample(i, "item-" + i));
        }

        long checksum = 0;
        for (Sample sample : samples) {
            checksum += sample.index() * 31L + sample.label().length();
        }
        return checksum;
    }

    public static void main(String[] args) {
        int count = args.length == 0 ? 25_000 : Integer.parseInt(args[0]);
        System.out.println("checksum=" + checksum(count));
    }
}
