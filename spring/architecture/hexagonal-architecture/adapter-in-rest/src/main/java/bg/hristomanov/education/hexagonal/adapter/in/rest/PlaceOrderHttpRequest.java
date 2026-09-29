package bg.hristomanov.education.hexagonal.adapter.in.rest;

import java.util.List;

public record PlaceOrderHttpRequest(
        String reference,
        String customerId,
        List<Item> items
) {

    public PlaceOrderHttpRequest {
        items = List.copyOf(items);
    }

    public record Item(
            String sku,
            int quantity
    ) {
    }
}
