package bg.hristomanov.education.outbox.api;

import bg.hristomanov.education.outbox.broker.BrokerMessage;
import bg.hristomanov.education.outbox.broker.InMemoryMessageBroker;
import bg.hristomanov.education.outbox.inbox.ConsumptionResult;
import bg.hristomanov.education.outbox.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/outbox-inbox")
public class OutboxInboxLabController {

    private final NaiveOrderService naiveOrderService;
    private final TransactionalOutboxOrderService outboxOrderService;
    private final OutboxRelayService relayService;
    private final IdempotentOrderCreatedConsumer consumer;
    private final InMemoryMessageBroker broker;
    private final OutboxInboxLabService labService;

    public OutboxInboxLabController(
            NaiveOrderService naiveOrderService,
            TransactionalOutboxOrderService outboxOrderService,
            OutboxRelayService relayService,
            IdempotentOrderCreatedConsumer consumer,
            InMemoryMessageBroker broker,
            OutboxInboxLabService labService
    ) {
        this.naiveOrderService = naiveOrderService;
        this.outboxOrderService = outboxOrderService;
        this.relayService = relayService;
        this.consumer = consumer;
        this.broker = broker;
        this.labService = labService;
    }

    @PostMapping("/reset")
    public LabState reset() {
        labService.reset();
        return labService.state();
    }

    @PostMapping("/naive/orders")
    public Map<String, Long> createNaive(
            @RequestBody CreateOrderRequest request,
            @RequestParam(defaultValue = "false") boolean stopAfterCommit
    ) {
        long orderId = naiveOrderService.createOrder(
                request.reference(),
                request.customerId(),
                request.totalAmount(),
                stopAfterCommit
        );
        return Map.of("orderId", orderId);
    }

    @PostMapping("/orders")
    public Map<String, Long> createWithOutbox(
            @RequestBody CreateOrderRequest request
    ) {
        long orderId = outboxOrderService.createOrder(
                request.reference(),
                request.customerId(),
                request.totalAmount()
        );
        return Map.of("orderId", orderId);
    }

    @PostMapping("/relay")
    public Map<String, Object> relay(
            @RequestParam(defaultValue = "false") boolean stopAfterPublish
    ) {
        return Map.of(
                "publishedEventId",
                relayService.publishNext(stopAfterPublish).orElse("NONE")
        );
    }

    @PostMapping("/consume/{messageIndex}")
    public Map<String, String> consume(
            @PathVariable int messageIndex,
            @RequestParam(defaultValue = "false") boolean failBeforeCommit
    ) {
        List<BrokerMessage> messages = broker.messages();

        if (messageIndex < 0 || messageIndex >= messages.size()) {
            throw new IllegalArgumentException(
                    "Unknown broker message index: " + messageIndex
            );
        }

        ConsumptionResult result = consumer.consume(
                messages.get(messageIndex),
                failBeforeCommit
        );

        return Map.of("result", result.name());
    }

    @GetMapping("/state")
    public LabState state() {
        return labService.state();
    }
}
