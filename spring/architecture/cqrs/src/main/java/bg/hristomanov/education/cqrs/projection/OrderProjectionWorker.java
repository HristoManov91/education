package bg.hristomanov.education.cqrs.projection;

import bg.hristomanov.education.cqrs.write.OrderWriteEntity;
import bg.hristomanov.education.cqrs.write.OrderWriteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Симулира asynchronous/eventually-consistent read-model updater.
 *
 * <p>Write transaction-ът може да commit-не преди read model refresh-а.
 * В този прозорец query side-ът е stale или projection-ът още липсва.</p>
 */
@Service
public class OrderProjectionWorker {

    private final ProjectionRefreshRequestRepository requestRepository;
    private final OrderWriteRepository orderRepository;
    private final OrderProjectionWriter projectionWriter;

    public OrderProjectionWorker(
            ProjectionRefreshRequestRepository requestRepository,
            OrderWriteRepository orderRepository,
            OrderProjectionWriter projectionWriter
    ) {
        this.requestRepository = requestRepository;
        this.orderRepository = orderRepository;
        this.projectionWriter = projectionWriter;
    }

    @Transactional
    public Optional<Long> refreshNext() {
        Optional<ProjectionRefreshRequest> next =
                requestRepository
                        .findFirstByProcessedAtIsNullOrderByCreatedAtAscIdAsc();

        if (next.isEmpty()) {
            return Optional.empty();
        }

        ProjectionRefreshRequest request = next.get();

        OrderWriteEntity order = orderRepository
                .findById(request.getOrderId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Projection source order disappeared: "
                                        + request.getOrderId()
                        )
                );

        projectionWriter.project(order);
        request.markProcessed(Instant.now());

        return Optional.of(order.getId());
    }

    @Transactional
    public int refreshAll() {
        int refreshed = 0;

        while (refreshNext().isPresent()) {
            refreshed++;
        }

        return refreshed;
    }
}
