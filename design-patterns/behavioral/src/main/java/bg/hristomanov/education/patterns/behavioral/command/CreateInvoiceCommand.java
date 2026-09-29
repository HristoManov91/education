package bg.hristomanov.education.patterns.behavioral.command;

import java.math.BigDecimal;

public class CreateInvoiceCommand implements Command<String> {

    private final InvoiceService invoiceService;
    private final String orderId;
    private final BigDecimal amount;

    public CreateInvoiceCommand(
            InvoiceService invoiceService,
            String orderId,
            BigDecimal amount
    ) {
        this.invoiceService = invoiceService;
        this.orderId = orderId;
        this.amount = amount;
    }

    @Override
    public String name() {
        return "create-invoice:" + orderId;
    }

    @Override
    public String execute() {
        return invoiceService.createInvoice(orderId, amount);
    }
}
