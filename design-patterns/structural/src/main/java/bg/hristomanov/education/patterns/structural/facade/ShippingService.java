package bg.hristomanov.education.patterns.structural.facade;

public class ShippingService {

    public String schedule(String reservationId) {
        return "SHIP-" + reservationId;
    }
}
