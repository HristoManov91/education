package bg.hristomanov.education.patterns.behavioral.templatemethod;

import java.util.Arrays;
import java.util.List;

public class JsonOrderImportJob extends AbstractOrderImportJob {

    @Override
    protected String formatName() {
        return "json";
    }

    @Override
    protected List<String> parse(String loaded) {
        String normalized = loaded
                .replace("[", "")
                .replace("]", "")
                .replace("\"", "");

        return Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    @Override
    protected void afterImport(List<String> orders, List<String> trace) {
        trace.add("json-audit");
    }
}
