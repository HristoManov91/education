package bg.hristomanov.education.dop;

/// Demonstrates **Markdown JavaDoc** (JEP 467, JDK 23+) in production-like
/// source. The snippet shows the same algorithm executed by this class.
///
/// Example:
///
/// {@snippet :
/// var example = new Tablets(1, 0, 1);
/// String result = MedicationOperations.describe(example);
/// }
///
/// For production documentation, prefer snippets sourced from compiled/tested
/// source files where practical; an inline snippet is not a runtime test.
public final class DocumentationExample {

    private DocumentationExample() {
    }

    public static String sampleDescription() {
        return MedicationOperations.describe(new Tablets(1, 0, 1));
    }
}
