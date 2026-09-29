package bg.hristomanov.education.patterns.behavioral.command;

import java.math.BigDecimal;

public class InvoiceService {

    public String createInvoice(String orderId, BigDecimal amount) {
        return "INV-" + orderId + "-" + amount;
    }
}
