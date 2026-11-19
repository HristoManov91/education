package bg.hristomanov.education.patterns.mastery.render;

import bg.hristomanov.education.patterns.mastery.model.ReportRequest;

/**
 * CSV demonstration with quoting and basic spreadsheet-formula mitigation.
 * NOT a universal spreadsheet security solution: OWASP documents Excel save/reopen
 * and application-specific bypasses: https://community.owasp.org/attacks/CSV_Injection
 */
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
        // OWASP warns apostrophe+quoting can fail after Excel save/reopen.
        // This is for illustration and MUST NOT be treated as production-approved sanitization.
        String sanitized = !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0
                ? "'" + value : value;
        return "\"" + sanitized.replace("\"", "\"\"") + "\"";
    }
}
