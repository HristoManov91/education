package bg.hristomanov.education.performance.runtime;

import java.util.Objects;

/**
 * Нарочно поддържан ръчен вариант: понякога разработчикът опитва да
 * специализира runtime dispatch с проверки по тип.
 * Добавянето на нов Shape вече изисква промяна на този метод.
 * Не твърдим, че винаги е по-бавен: сравнява се с JMH, не по интуиция.
 */
public final class ManualShapeDispatch {

    private ManualShapeDispatch() {
    }

    public static double doubleArea(ShapeDispatch.Shape shape) {
        Objects.requireNonNull(shape, "shape");
        if (shape instanceof ShapeDispatch.Circle circle) {
            return 2.0 * Math.PI * circle.radius() * circle.radius();
        }
        if (shape instanceof ShapeDispatch.Rectangle rectangle) {
            return 2.0 * rectangle.width() * rectangle.height();
        }
        if (shape instanceof ShapeDispatch.Triangle triangle) {
            return triangle.base() * triangle.height();
        }
        throw new IllegalArgumentException("Manually specialized dispatch omits shape: "
                + shape.getClass().getName());
    }
}
