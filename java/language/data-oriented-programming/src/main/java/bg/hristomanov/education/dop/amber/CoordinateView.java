package bg.hristomanov.education.dop.amber;

/**
 * Stable public accessor contract. An ordinary Java 25 interface does NOT
 * automatically support record-pattern deconstruction.
 */
public interface CoordinateView {

    int x();

    int y();
}
