package bg.hristomanov.education.jfr.service;

import bg.hristomanov.education.performance.runtime.ShapeDispatch;
import org.springframework.stereotype.Service;

/**
 * Същият ShapeDispatch като в JMH, но през Spring Boot HTTP request lifecycle.
 * Целта е сравнение на контекстите, не извеждане на HTTP p99 от JMH ns/op.
 */
@Service
public class RuntimeAreaService {

    public record Result(int shapeTypes, int rounds, long evaluatedShapes, double doubledAreaSum) {
    }

    public Result calculate(int shapeTypes, int rounds) {
        if (shapeTypes < 1 || shapeTypes > 3) {
            throw new IllegalArgumentException("shapeTypes must be between 1 and 3");
        }
        if (rounds < 1 || rounds > 100_000) {
            throw new IllegalArgumentException("rounds must be between 1 and 100000");
        }
        ShapeDispatch.Shape[] shapes = ShapeDispatch.sampleShapes(shapeTypes);
        double sum = ShapeDispatch.sumDoubleAreas(shapes, rounds);
        return new Result(shapeTypes, rounds, (long) shapeTypes * rounds, sum);
    }
}
