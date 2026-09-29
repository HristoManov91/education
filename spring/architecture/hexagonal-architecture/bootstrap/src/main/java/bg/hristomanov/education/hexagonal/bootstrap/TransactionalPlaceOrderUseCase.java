package bg.hristomanov.education.hexagonal.bootstrap;

import bg.hristomanov.education.hexagonal.port.in.OrderView;
import bg.hristomanov.education.hexagonal.port.in.PlaceOrderCommand;
import bg.hristomanov.education.hexagonal.port.in.PlaceOrderUseCase;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

/**
 * Infrastructure decorator around an input port.
 *
 * <p>Transaction management is a Spring concern at the edge of the core,
 * not an annotation dependency inside OrderApplicationService.</p>
 */
public final class TransactionalPlaceOrderUseCase
        implements PlaceOrderUseCase {

    private final PlaceOrderUseCase delegate;
    private final TransactionTemplate transactionTemplate;

    public TransactionalPlaceOrderUseCase(
            PlaceOrderUseCase delegate,
            PlatformTransactionManager transactionManager
    ) {
        this.delegate = delegate;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    @Override
    public OrderView place(PlaceOrderCommand command) {
        OrderView result = transactionTemplate.execute(
                status -> delegate.place(command)
        );

        return Objects.requireNonNull(
                result,
                "PlaceOrderUseCase returned null"
        );
    }
}
