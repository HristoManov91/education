package bg.hristomanov.education.patterns.behavioral.templatemethod;

import java.util.List;

public record ImportResult(
        int importedCount,
        List<String> trace
) {
    public ImportResult {
        trace = List.copyOf(trace);
    }
}
