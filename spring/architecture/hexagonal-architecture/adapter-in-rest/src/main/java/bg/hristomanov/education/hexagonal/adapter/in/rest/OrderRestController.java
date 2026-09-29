package bg.hristomanov.education.hexagonal.adapter.in.rest;

import bg.hristomanov.education.hexagonal.port.in.GetOrderUseCase;
import bg.hristomanov.education.hexagonal.port.in.OrderView;
import bg.hristomanov.education.hexagonal.port.in.PlaceOrderCommand;
import bg.hristomanov.education.hexagonal.port.in.PlaceOrderUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hexagonal/orders")
public class OrderRestController {

    private final PlaceOrderUseCase placeOrder;
    private final GetOrderUseCase getOrder;

    public OrderRestController(
            PlaceOrderUseCase placeOrder,
            GetOrderUseCase getOrder
    ) {
        this.placeOrder = placeOrder;
        this.getOrder = getOrder;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderHttpResponse place(
            @RequestBody PlaceOrderHttpRequest request
    ) {
        PlaceOrderCommand command = new PlaceOrderCommand(
                request.reference(),
                request.customerId(),
                request.items()
                        .stream()
                        .map(item ->
                                new PlaceOrderCommand.Item(
                                        item.sku(),
                                        item.quantity()
                                )
                        )
                        .toList()
        );

        OrderView result = placeOrder.place(command);
        return OrderHttpResponse.from(result);
    }

    @GetMapping("/{orderId}")
    public OrderHttpResponse get(@PathVariable long orderId) {
        return OrderHttpResponse.from(getOrder.get(orderId));
    }
}
