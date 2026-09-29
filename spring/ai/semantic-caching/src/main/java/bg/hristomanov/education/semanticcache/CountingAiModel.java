package bg.hristomanov.education.semanticcache;

import java.util.concurrent.atomic.AtomicInteger;

public class CountingAiModel {

    private final AtomicInteger calls =
            new AtomicInteger();

    public String generate(String query) {
        int call = calls.incrementAndGet();

        return "generated["
                + call
                + "]: "
                + query;
    }

    public int callCount() {
        return calls.get();
    }
}
