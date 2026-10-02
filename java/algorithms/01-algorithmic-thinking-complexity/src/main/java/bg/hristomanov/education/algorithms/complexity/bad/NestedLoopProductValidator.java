package bg.hristomanov.education.algorithms.complexity.bad;

import bg.hristomanov.education.algorithms.complexity.model.OrderLine;
import bg.hristomanov.education.algorithms.complexity.model.Product;
import bg.hristomanov.education.algorithms.complexity.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Наивен вариант, който за всеки order line обхожда целия продуктов каталог.
 *
 * <p>Кодът изглежда напълно разумно при малки входове, но logical work-ът расте
 * като O(orders * products). Точно този тип скрито вложено търсене често се
 * появява в production код при enrichment, validation и matching задачи.</p>
 */
public final class NestedLoopProductValidator {

    public ValidationResult validate(List<OrderLine> orderLines, List<Product> products) {
        List<Long> invalidProductIds = new ArrayList<>();
        long comparisons = 0;

        for (OrderLine orderLine : orderLines) {
            boolean activeProductFound = false;

            for (Product product : products) {
                comparisons++;
                if (product.id() == orderLine.productId() && product.active()) {
                    activeProductFound = true;
                    break;
                }
            }

            if (!activeProductFound) {
                invalidProductIds.add(orderLine.productId());
            }
        }

        return new ValidationResult(List.copyOf(invalidProductIds), comparisons, 0, 0);
    }
}
