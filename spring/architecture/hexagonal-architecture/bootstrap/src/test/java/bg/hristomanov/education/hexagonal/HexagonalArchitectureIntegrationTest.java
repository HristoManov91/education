package bg.hristomanov.education.hexagonal;

import bg.hristomanov.education.hexagonal.adapter.in.rest.OrderHttpResponse;
import bg.hristomanov.education.hexagonal.adapter.in.rest.OrderRestController;
import bg.hristomanov.education.hexagonal.adapter.in.rest.PlaceOrderHttpRequest;
import bg.hristomanov.education.hexagonal.adapter.out.jpa.SpringDataOrderRepository;
import bg.hristomanov.education.hexagonal.application.OrderApplicationService;
import bg.hristomanov.education.hexagonal.port.in.GetOrderUseCase;
import bg.hristomanov.education.hexagonal.port.in.OrderView;
import bg.hristomanov.education.hexagonal.port.in.PlaceOrderCommand;
import bg.hristomanov.education.hexagonal.port.in.PlaceOrderUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HexagonalArchitectureIntegrationTest {

    private final PlaceOrderUseCase placeOrder;
    private final GetOrderUseCase getOrder;
    private final OrderRestController restController;
    private final SpringDataOrderRepository springDataOrderRepository;

    @Autowired
    HexagonalArchitectureIntegrationTest(
            PlaceOrderUseCase placeOrder,
            GetOrderUseCase getOrder,
            OrderRestController restController,
            SpringDataOrderRepository springDataOrderRepository
    ) {
        this.placeOrder = placeOrder;
        this.getOrder = getOrder;
        this.restController = restController;
        this.springDataOrderRepository = springDataOrderRepository;
    }

    @BeforeEach
    void cleanOrders() {
        springDataOrderRepository.deleteAll();
    }

    @Test
    void coreUseCaseWorksThroughJpaDrivenAdapter() {
        OrderView created = placeOrder.place(
                new PlaceOrderCommand(
                        "ORD-HEX-JPA",
                        "C-100",
                        List.of(
                                new PlaceOrderCommand.Item("KEYBOARD", 1),
                                new PlaceOrderCommand.Item("MOUSE", 2)
                        )
                )
        );

        assertThat(created.totalAmount()).isEqualByComparingTo("180.00");
        assertThat(springDataOrderRepository.count()).isEqualTo(1);

        OrderView reloaded = getOrder.get(created.id());

        assertThat(reloaded).isEqualTo(created);
    }

    @Test
    void restAdapterMapsHttpDtoToInputPortAndBack() {
        OrderHttpResponse created = restController.place(
                new PlaceOrderHttpRequest(
                        "ORD-HEX-REST",
                        "C-200",
                        List.of(
                                new PlaceOrderHttpRequest.Item(
                                        "MONITOR",
                                        1
                                )
                        )
                )
        );

        assertThat(created.totalAmount()).isEqualByComparingTo("300.00");

        OrderHttpResponse reloaded =
                restController.get(created.id());

        assertThat(reloaded).isEqualTo(created);
    }

    @Test
    void coreApplicationServiceHasNoSpringOrJpaAnnotations() {
        List<String> annotationTypes = Arrays
                .stream(OrderApplicationService.class.getAnnotations())
                .map(Annotation::annotationType)
                .map(Class::getName)
                .toList();

        assertThat(annotationTypes)
                .noneMatch(name ->
                        name.startsWith("org.springframework.")
                                || name.startsWith("jakarta.persistence.")
                );
    }
}
