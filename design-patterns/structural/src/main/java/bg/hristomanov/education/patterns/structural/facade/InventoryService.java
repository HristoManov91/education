package bg.hristomanov.education.patterns.structural.facade;

public class InventoryService {

    public String reserve(String sku, int quantity) {
        return "RES-" + sku + "-" + quantity;
    }
}
