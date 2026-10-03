package bg.hristomanov.education.algorithms.complexity.good;

import bg.hristomanov.education.algorithms.complexity.model.OrderLine;
import bg.hristomanov.education.algorithms.complexity.model.Product;
import bg.hristomanov.education.algorithms.complexity.model.ValidationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Индексира активните product id-та веднъж и след това прави membership lookup.
 *
 * <p>Тук използваме HashSet само като готов lookup индекс. Как hashing работи
 * отвътре ще се учи в отделния hashing модул. За тази тема важен е mental
 * model-ът: понякога по-добрата data structure променя самата complexity форма.</p>
 */
public final class IndexedProductValidator {

    public ValidationResult validate(List<OrderLine> orderLines, List<Product> products) {
        Set<Long> activeProductIds = new HashSet<>();
        long indexBuildOperations = 0;

        for (Product product : products) {
            indexBuildOperations++;
            if (product.active()) {
                activeProductIds.add(product.id());
            }
        }

        List<Long> invalidProductIds = new ArrayList<>();
        long membershipChecks = 0;

        for (OrderLine orderLine : orderLines) {
            membershipChecks++;
            if (!activeProductIds.contains(orderLine.productId())) {
                invalidProductIds.add(orderLine.productId());
            }
        }

        return new ValidationResult(
                List.copyOf(invalidProductIds),
                0,
                indexBuildOperations,
                membershipChecks
        );
    }
}
