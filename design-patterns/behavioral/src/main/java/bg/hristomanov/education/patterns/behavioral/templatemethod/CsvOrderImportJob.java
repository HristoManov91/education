package bg.hristomanov.education.patterns.behavioral.templatemethod;

import java.util.Arrays;
import java.util.List;

public class CsvOrderImportJob extends AbstractOrderImportJob {

    @Override
    protected String formatName() {
        return "csv";
    }

    @Override
    protected List<String> parse(String loaded) {
        return Arrays.stream(loaded.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
