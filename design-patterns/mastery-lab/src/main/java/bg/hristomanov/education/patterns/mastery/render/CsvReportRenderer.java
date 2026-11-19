package bg.hristomanov.education.patterns.mastery.render;

import bg.hristomanov.education.patterns.mastery.model.ReportRequest;

/** A small CSV renderer with quoting and basic spreadsheet-formula neutralization. */
public final class CsvReportRenderer implements ReportRenderer {
    @Override public String format() { return "CSV"; }

    @Override public String render(ReportRequest request) {
        StringBuilder csv = new StringBuilder("reference,item\r\n");
        for (String item : request.items()) {
            csv.append(cell(request.reference())).append(',').append(cell(item)).append("\r\n");
        }
        return csv.toString();
    }

    private static String cell(String value) {
        // For a spreadsheet-oriented CSV, quote fields AND neutralize a leading formula token.
        // This is a demonstration, not a universal CSV or spreadsheet security library.
        String sanitized = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0
                ? "'" + value : value;
        return "\"" + sanitized.replace("\"", "\"\"") + "\"";
    }
}
