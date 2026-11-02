package bg.hristomanov.education.performance.runtime;

/**
 * Ръчен наблюдателен сценарий за compilation/inlining логове, НЕ benchmark.
 * Warm-up с един тип и последващата смяна не гарантират deoptimization:
 * поведението на HotSpot е version- и profile-dependent.
 */
public final class ShapeDispatchScenario {

    private static volatile double observed;

    private ShapeDispatchScenario() {
    }

    public static void main(String[] args) {
        int rounds = args.length == 0 ? 100_000 : Integer.parseInt(args[0]);
        if (rounds < 1 || rounds > 5_000_000) {
            throw new IllegalArgumentException("rounds must be between 1 and 5,000,000");
        }

        ShapeDispatch.Shape[] oneType = ShapeDispatch.sampleShapes(1);
        ShapeDispatch.Shape[] threeTypes = ShapeDispatch.sampleShapes(3);

        // Първо един runtime type на конкретната call site.
        double first = ShapeDispatch.sumDoubleAreas(oneType, rounds);
        // После на същата call site се подават още два типа.
        double second = ShapeDispatch.sumDoubleAreas(threeTypes, rounds);
        observed = first + second;

        System.out.printf("mono=%.4f, mixed=%.4f, checksum=%.4f%n",
                first, second, observed);
    }
}
