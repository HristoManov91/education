package bg.hristomanov.education.performance.runtime;

import java.util.Objects;

/**
 * Един и същ абстрактен contract за малък и за разрастващ се модел.
 * JVM може да специализира virtual calls според реално наблюдаваните типове,
 * без business кодът да изброява всички имплементации.
 */
public final class ShapeDispatch {

    private ShapeDispatch() {
    }

    public interface Shape {
        double area();
    }

    public record Circle(double radius) implements Shape {
        public Circle {
            requirePositiveFinite(radius, "radius");
        }

        @Override
        public double area() {
            return Math.PI * radius * radius;
        }
    }

    public record Rectangle(double width, double height) implements Shape {
        public Rectangle {
            requirePositiveFinite(width, "width");
            requirePositiveFinite(height, "height");
        }

        @Override
        public double area() {
            return width * height;
        }
    }

    public record Triangle(double base, double height) implements Shape {
        public Triangle {
            requirePositiveFinite(base, "base");
            requirePositiveFinite(height, "height");
        }

        @Override
        public double area() {
            return base * height / 2.0;
        }
    }

    public static double doubleArea(Shape shape) {
        return 2.0 * Objects.requireNonNull(shape, "shape").area();
    }

    /**
     * Една call site за различни действителни типове.
     * Конкретното JIT решение зависи от профила на изпълнение, JVM и flags.
     */
    public static double sumDoubleAreas(Shape[] shapes, int rounds) {
        Objects.requireNonNull(shapes, "shapes");
        if (shapes.length == 0 || rounds <= 0) {
            throw new IllegalArgumentException("Shapes must not be empty and rounds must be positive");
        }
        double total = 0.0;
        for (int round = 0; round < rounds; round++) {
            for (Shape shape : shapes) {
                total += doubleArea(shape);
            }
        }
        return total;
    }

    public static Shape[] sampleShapes(int distinctTypes) {
        return switch (distinctTypes) {
            case 1 -> new Shape[]{new Circle(2.0)};
            case 2 -> new Shape[]{new Circle(2.0), new Rectangle(2.0, 3.0)};
            case 3 -> new Shape[]{new Circle(2.0), new Rectangle(2.0, 3.0),
                    new Triangle(4.0, 3.0)};
            default -> throw new IllegalArgumentException("distinctTypes must be 1, 2 or 3");
        };
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }
}
