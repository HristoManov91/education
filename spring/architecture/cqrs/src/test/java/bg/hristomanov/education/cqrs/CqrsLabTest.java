package bg.hristomanov.education.cqrs;

import bg.hristomanov.education.cqrs.projection.OrderProjectionWorker;
import bg.hristomanov.education.cqrs.projection.ProjectionRefreshRequestRepository;
import bg.hristomanov.education.cqrs.read.OrderQueryService;
import bg.hristomanov.education.cqrs.read.OrderSummaryDto;
import bg.hristomanov.education.cqrs.read.OrderSummaryRepository;
import bg.hristomanov.education.cqrs.write.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CqrsLabTest {

    private final OrderCommandService commandService;
    private final OrderQueryService queryService;
    private final OrderProjectionWorker projectionWorker;
    private final OrderWriteRepository writeRepository;
    private final OrderSummaryRepository readRepository;
    private final ProjectionRefreshRequestRepository refreshRepository;

    @Autowired
    CqrsLabTest(
            OrderCommandService commandService,
            OrderQueryService queryService,
            OrderProjectionWorker projectionWorker,
            OrderWriteRepository writeRepository,
            OrderSummaryRepository readRepository,
            ProjectionRefreshRequestRepository refreshRepository
    ) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.projectionWorker = projectionWorker;
        this.writeRepository = writeRepository;
        this.readRepository = readRepository;
        this.refreshRepository = refreshRepository;
    }

    @BeforeEach
    void clean() {
        refreshRepository.deleteAll();
        readRepository.deleteAll();
        writeRepository.deleteAll();
    }

    @Test
    void synchronousCqrsUsesDifferentWriteAndReadModelsWithoutConsistencyLag() {
        long orderId = commandService.create(
                createCommand("ORD-SYNC", "C-1"),
                ProjectionMode.SYNCHRONOUS
        );

        OrderWriteEntity writeModel = writeRepository
                .findById(orderId)
                .orElseThrow();

        OrderSummaryDto readModel = queryService.get(orderId);

        assertThat(writeModel.getLines()).hasSize(2);
        assertThat(writeModel.getTotalAmount()).isEqualByComparingTo("180.00");

        assertThat(readModel.orderId()).isEqualTo(orderId);
        assertThat(readModel.reference()).isEqualTo("ORD-SYNC");
        assertThat(readModel.status()).isEqualTo("NEW");
        assertThat(readModel.totalAmount()).isEqualByComparingTo("180.00");
        assertThat(readModel.itemCount()).isEqualTo(3);
        assertThat(readModel.displayLabel())
                .isEqualTo("ORD-SYNC | 3 items | 180.00");

        /*
         * Read DTO-то няма child entities или behavior methods.
         * То е query shape, не write aggregate.
         */
        assertThat(readModel.sourceVersion()).isEqualTo(writeModel.getVersion());
        assertThat(refreshRepository.countByProcessedAtIsNull()).isZero();
    }

    @Test
    void deferredProjectionCreatesAnIntentionalStaleWindowThenCatchesUp() {
        long orderId = commandService.create(
                createCommand("ORD-DEFERRED", "C-2"),
                ProjectionMode.DEFERRED
        );

        assertThat(writeRepository.findById(orderId)).isPresent();
        assertThat(readRepository.findById(orderId)).isEmpty();
        assertThat(refreshRepository.countByProcessedAtIsNull()).isEqualTo(1);

        assertThatThrownBy(() -> queryService.get(orderId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Read model not available");

        assertThat(projectionWorker.refreshNext()).contains(orderId);

        OrderSummaryDto readModel = queryService.get(orderId);

        assertThat(readModel.reference()).isEqualTo("ORD-DEFERRED");
        assertThat(readModel.status()).isEqualTo("NEW");
        assertThat(refreshRepository.countByProcessedAtIsNull()).isZero();
    }

    @Test
    void deferredUpdateCanMakeReadModelTemporarilyStale() {
        long orderId = commandService.create(
                createCommand("ORD-STALE", "C-3"),
                ProjectionMode.SYNCHRONOUS
        );

        OrderSummaryDto before = queryService.get(orderId);

        assertThat(before.status()).isEqualTo("NEW");
        assertThat(before.sourceVersion()).isZero();

        commandService.markPaid(orderId, ProjectionMode.DEFERRED);

        OrderWriteEntity currentWrite = writeRepository
                .findById(orderId)
                .orElseThrow();
        OrderSummaryDto staleRead = queryService.get(orderId);

        assertThat(currentWrite.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(currentWrite.getVersion()).isEqualTo(1);

        /*
         * Това е core eventual-consistency scenario:
         * command side вече е PAID, query side още вижда NEW.
         */
        assertThat(staleRead.status()).isEqualTo("NEW");
        assertThat(staleRead.sourceVersion()).isZero();
        assertThat(refreshRepository.countByProcessedAtIsNull()).isEqualTo(1);

        projectionWorker.refreshNext();

        OrderSummaryDto caughtUp = queryService.get(orderId);

        assertThat(caughtUp.status()).isEqualTo("PAID");
        assertThat(caughtUp.sourceVersion()).isEqualTo(1);
    }

    @Test
    void invalidCommandCreatesNeitherWriteNorReadState() {
        CreateOrderCommand invalid = new CreateOrderCommand(
                "ORD-INVALID",
                "C-4",
                List.of(
                        new CreateOrderCommand.Line(
                                "BROKEN",
                                0,
                                new BigDecimal("10.00")
                        )
                )
        );

        assertThatThrownBy(() ->
                commandService.create(
                        invalid,
                        ProjectionMode.SYNCHRONOUS
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be positive");

        assertThat(writeRepository.count()).isZero();
        assertThat(readRepository.count()).isZero();
        assertThat(refreshRepository.count()).isZero();
    }

    @Test
    void queryModelIsOptimizedForCustomerReadUseCase() {
        commandService.create(
                createCommand("ORD-CUSTOMER-1", "C-5"),
                ProjectionMode.SYNCHRONOUS
        );
        commandService.create(
                createCommand("ORD-CUSTOMER-2", "C-5"),
                ProjectionMode.SYNCHRONOUS
        );
        commandService.create(
                createCommand("ORD-OTHER", "C-OTHER"),
                ProjectionMode.SYNCHRONOUS
        );

        List<OrderSummaryDto> customerOrders =
                queryService.findByCustomer("C-5");

        assertThat(customerOrders)
                .extracting(OrderSummaryDto::reference)
                .containsExactlyInAnyOrder(
                        "ORD-CUSTOMER-1",
                        "ORD-CUSTOMER-2"
                );
    }

    private CreateOrderCommand createCommand(
            String reference,
            String customerId
    ) {
        return new CreateOrderCommand(
                reference,
                customerId,
                List.of(
                        new CreateOrderCommand.Line(
                                "KEYBOARD",
                                1,
                                new BigDecimal("100.00")
                        ),
                        new CreateOrderCommand.Line(
                                "MOUSE",
                                2,
                                new BigDecimal("40.00")
                        )
                )
        );
    }
}
