package bg.hristomanov.education.dop;

/**
 * Closed set of dosage variants. A new permitted subtype forces exhaustive
 * switches over Dose to be revisited at compile time.
 */
public sealed interface Dose permits Tablets, Infusion {
}
