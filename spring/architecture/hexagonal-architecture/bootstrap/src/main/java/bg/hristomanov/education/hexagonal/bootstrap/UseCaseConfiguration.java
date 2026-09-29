package bg.hristomanov.education.hexagonal.bootstrap;

import bg.hristomanov.education.hexagonal.application.OrderApplicationService;
import bg.hristomanov.education.hexagonal.port.in.GetOrderUseCase;
import bg.hristomanov.education.hexagonal.port.in.PlaceOrderUseCase;
import bg.hristomanov.education.hexagonal.port.out.OrderRepositoryPort;
import bg.hristomanov.education.hexagonal.port.out.ProductCatalogPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {

    @Bean
    public OrderApplicationService coreOrderApplication(
            OrderRepositoryPort orderRepository,
            ProductCatalogPort productCatalog
    ) {
        return new OrderApplicationService(
                orderRepository,
                productCatalog
        );
    }

    @Bean
    @Primary
    public PlaceOrderUseCase placeOrderUseCase(
            OrderApplicationService coreOrderApplication,
            PlatformTransactionManager transactionManager
    ) {
        return new TransactionalPlaceOrderUseCase(
                coreOrderApplication,
                transactionManager
        );
    }

    @Bean
    @Primary
    public GetOrderUseCase getOrderUseCase(
            OrderApplicationService coreOrderApplication,
            PlatformTransactionManager transactionManager
    ) {
        return new TransactionalGetOrderUseCase(
                coreOrderApplication,
                transactionManager
        );
    }
}
