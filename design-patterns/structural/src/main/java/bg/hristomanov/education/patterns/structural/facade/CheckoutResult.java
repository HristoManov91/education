package bg.hristomanov.education.patterns.structural.facade;

public record CheckoutResult(
        String reservationId,
        String paymentId,
        String shipmentId
) {
}
