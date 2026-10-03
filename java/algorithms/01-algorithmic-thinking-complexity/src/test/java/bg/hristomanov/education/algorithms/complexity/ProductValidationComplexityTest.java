package bg.hristomanov.education.algorithms.complexity;

import bg.hristomanov.education.algorithms.complexity.bad.NestedLoopProductValidator;
import bg.hristomanov.education.algorithms.complexity.good.IndexedProductValidator;
import bg.hristomanov.education.algorithms.complexity.model.OrderLine;
import bg.hristomanov.education.algorithms.complexity.model.Product;
import bg.hristomanov.education.algorithms.complexity.model.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductValidationComplexityTest {

    private final NestedLoopProductValidator badValidator = new NestedLoopProductValidator();
    private final IndexedProductValidator goodValidator = new IndexedProductValidator();

    @Test
    void bothImplementationsPreserveBusinessSemantics() {
        List<Product> products = List.of(
                new Product(1, true),
                new Product(2, false),
                new Product(3, true)
        );
        List<OrderLine> orderLines = List.of(
                new OrderLine(1),
                new OrderLine(2),
                new OrderLine(99)
        );

        ValidationResult badResult = badValidator.validate(orderLines, products);
        ValidationResult goodResult = goodValidator.validate(orderLines, products);

        assertThat(badResult.invalidProductIds()).containsExactly(2L, 99L);
        assertThat(goodResult.invalidProductIds()).isEqualTo(badResult.invalidProductIds());
    }

    @Test
    void indexedLookupChangesGrowthFromProductToSumOfInputSizes() {
        ValidationResult bad100 = badValidator.validate(invalidOrderLines(100), activeProducts(100));
        ValidationResult bad1000 = badValidator.validate(invalidOrderLines(1_000), activeProducts(1_000));

        ValidationResult good100 = goodValidator.validate(invalidOrderLines(100), activeProducts(100));
        ValidationResult good1000 = goodValidator.validate(invalidOrderLines(1_000), activeProducts(1_000));

        assertThat(bad100.comparisons()).isEqualTo(10_000);
        assertThat(bad1000.comparisons()).isEqualTo(1_000_000);
        assertThat(bad1000.comparisons() / bad100.comparisons()).isEqualTo(100);

        assertThat(good100.totalLogicalOperations()).isEqualTo(200);
        assertThat(good1000.totalLogicalOperations()).isEqualTo(2_000);
        assertThat(good1000.totalLogicalOperations() / good100.totalLogicalOperations()).isEqualTo(10);
    }

    private List<Product> activeProducts(int count) {
        List<Product> products = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            products.add(new Product(i, true));
        }
        return products;
    }

    private List<OrderLine> invalidOrderLines(int count) {
        List<OrderLine> orderLines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            orderLines.add(new OrderLine(-1L - i));
        }
        return orderLines;
    }
}
