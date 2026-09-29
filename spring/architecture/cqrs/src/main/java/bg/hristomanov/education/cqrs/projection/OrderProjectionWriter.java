package bg.hristomanov.education.cqrs.projection;

import bg.hristomanov.education.cqrs.read.OrderSummaryProjection;
import bg.hristomanov.education.cqrs.read.OrderSummaryRepository;
import bg.hristomanov.education.cqrs.write.OrderLineEntity;
import bg.hristomanov.education.cqrs.write.OrderWriteEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Превежда write aggregate-а към query-optimized read model.
 *
 * <p>Read model-ът нарочно дублира/денормализира данни: status е String,
 * itemCount е предварително изчислен, а displayLabel е готов за UI/read use case.
 * Това не е source of truth за business invariants.</p>
 */
@Component
public class OrderProjectionWriter {

    private final OrderSummaryRepository repository;

    public OrderProjectionWriter(OrderSummaryRepository repository) {
        this.repository = repository;
    }

    public void project(OrderWriteEntity order) {
        int itemCount = order.getLines()
                .stream()
                .mapToInt(OrderLineEntity::getQuantity)
                .sum();

        String displayLabel = order.getReference()
                + " | "
                + itemCount
                + " items | "
                + order.getTotalAmount();

        OrderSummaryProjection projection = repository
                .findById(order.getId())
                .orElseGet(() ->
                        new OrderSummaryProjection(
                                order.getId(),
                                order.getReference(),
                                order.getCustomerId(),
                                order.getStatus().name(),
                                order.getTotalAmount(),
                                itemCount,
                                displayLabel,
                                order.getVersion(),
                                Instant.now()
                        )
                );

        if (repository.existsById(order.getId())) {
            projection.refresh(
                    order.getStatus().name(),
                    order.getTotalAmount(),
                    itemCount,
                    displayLabel,
                    order.getVersion(),
                    Instant.now()
            );
        }

        repository.save(projection);
    }
}
