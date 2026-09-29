package bg.hristomanov.education.hexagonal.bootstrap;

import bg.hristomanov.education.hexagonal.port.in.GetOrderUseCase;
import bg.hristomanov.education.hexagonal.port.in.OrderView;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

public final class TransactionalGetOrderUseCase
        implements GetOrderUseCase {

    private final GetOrderUseCase delegate;
    private final TransactionTemplate transactionTemplate;

    public TransactionalGetOrderUseCase(
            GetOrderUseCase delegate,
            PlatformTransactionManager transactionManager
    ) {
        this.delegate = delegate;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
        this.transactionTemplate.setReadOnly(true);
    }

    @Override
    public OrderView get(long orderId) {
        OrderView result = transactionTemplate.execute(
                status -> delegate.get(orderId)
        );

        return Objects.requireNonNull(
                result,
                "GetOrderUseCase returned null"
        );
    }
}
