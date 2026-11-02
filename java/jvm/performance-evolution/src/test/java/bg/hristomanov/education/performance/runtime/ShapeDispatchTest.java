package bg.hristomanov.education.performance.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class ShapeDispatchTest {

    @Test
    void bothImplementationsAgreeForKnownShapesAndRounds() {
        for (int types = 1; types <= 3; types++) {
            ShapeDispatch.Shape[] shapes = ShapeDispatch.sampleShapes(types);
            double manual = 0.0;
            for (ShapeDispatch.Shape shape : shapes) {
                manual += ManualShapeDispatch.doubleArea(shape);
            }
            assertThat(ShapeDispatch.sumDoubleAreas(shapes, 3))
                    .isCloseTo(manual * 3, offset(0.000000001));
        }
    }

    @Test
    void normalPolymorphismSupportsNewImplementationsWithoutEditingCalculator() {
        ShapeDispatch.Shape newShape = new ShapeDispatch.Shape() {
            @Override
            public double area() {
                return 7.5;
            }
        };

        assertThat(ShapeDispatch.doubleArea(newShape)).isEqualTo(15.0);
        assertThatThrownBy(() -> ManualShapeDispatch.doubleArea(newShape))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("omits shape");
    }

    @Test
    void invalidInputsFailRatherThanDistortMeasurement() {
        assertThatThrownBy(() -> ShapeDispatch.sampleShapes(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShapeDispatch.sumDoubleAreas(new ShapeDispatch.Shape[0], 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShapeDispatch.sumDoubleAreas(ShapeDispatch.sampleShapes(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShapeDispatch.Circle(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShapeDispatch.Rectangle(1.0, -3.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShapeDispatch.doubleArea(null))
                .isInstanceOf(NullPointerException.class);
    }
}
