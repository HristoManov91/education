package bg.hristomanov.education.cqrs.api;

import bg.hristomanov.education.cqrs.projection.OrderProjectionWorker;
import bg.hristomanov.education.cqrs.read.OrderQueryService;
import bg.hristomanov.education.cqrs.read.OrderSummaryDto;
import bg.hristomanov.education.cqrs.write.CreateOrderCommand;
import bg.hristomanov.education.cqrs.write.OrderCommandService;
import bg.hristomanov.education.cqrs.write.ProjectionMode;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cqrs")
public class CqrsController {

    private final OrderCommandService commandService;
    private final OrderQueryService queryService;
    private final OrderProjectionWorker projectionWorker;
    private final CqrsLabService labService;

    public CqrsController(
            OrderCommandService commandService,
            OrderQueryService queryService,
            OrderProjectionWorker projectionWorker,
            CqrsLabService labService
    ) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.projectionWorker = projectionWorker;
        this.labService = labService;
    }

    @PostMapping("/orders")
    public CommandResult create(
            @RequestBody CreateOrderRequest request,
            @RequestParam(defaultValue = "SYNCHRONOUS")
            ProjectionMode projectionMode
    ) {
        CreateOrderCommand command = new CreateOrderCommand(
                request.reference(),
                request.customerId(),
                request.lines()
                        .stream()
                        .map(line ->
                                new CreateOrderCommand.Line(
                                        line.sku(),
                                        line.quantity(),
                                        line.unitPrice()
                                )
                        )
                        .toList()
        );

        long orderId = commandService.create(command, projectionMode);

        return new CommandResult(orderId, projectionMode.name());
    }

    @PostMapping("/orders/{orderId}/pay")
    public CommandResult pay(
            @PathVariable long orderId,
            @RequestParam(defaultValue = "SYNCHRONOUS")
            ProjectionMode projectionMode
    ) {
        commandService.markPaid(orderId, projectionMode);
        return new CommandResult(orderId, projectionMode.name());
    }

    @GetMapping("/orders/{orderId}")
    public OrderSummaryDto get(@PathVariable long orderId) {
        return queryService.get(orderId);
    }

    @GetMapping("/customers/{customerId}/orders")
    public List<OrderSummaryDto> byCustomer(
            @PathVariable String customerId
    ) {
        return queryService.findByCustomer(customerId);
    }

    @PostMapping("/projections/refresh-next")
    public Map<String, Object> refreshNext() {
        return Map.of(
                "orderId",
                projectionWorker.refreshNext()
                        .map(Object.class::cast)
                        .orElse("NONE")
        );
    }

    @GetMapping("/state/{orderId}")
    public CqrsState state(@PathVariable long orderId) {
        return labService.state(orderId);
    }
}
