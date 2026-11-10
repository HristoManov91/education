package bg.hristomanov.education.instrumentation.sample;

/**
 * Стартира се в отделен JVM процес, за да докажем реалния premain lifecycle.
 */
public final class PriceApplication {

    private PriceApplication() {
    }

    public static void main(String[] args) {
        PricingService service = new PricingService();
        if (args.length > 0 && "failure".equals(args[0])) {
            service.quoteCents(-1, 700);
        }
        System.out.println("TOTAL_CENTS=" + service.quoteCents(3, 700));
    }
}
