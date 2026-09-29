package bg.hristomanov.education.hexagonal.port.in;

import java.util.List;

public record PlaceOrderCommand(
        String reference,
        String customerId,
        List<Item> items
) {

    public PlaceOrderCommand {
        items = List.copyOf(items);
    }

    public record Item(
            String sku,
            int quantity
    ) {
    }
}
