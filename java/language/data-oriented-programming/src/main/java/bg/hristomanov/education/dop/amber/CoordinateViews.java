package bg.hristomanov.education.dop.amber;

import java.util.Objects;

/**
 * Factory exposes only the public interface, not the package-private
 * implementation. This is source-level API design, not a security sandbox.
 */
public final class CoordinateViews {

    private CoordinateViews() {
    }

    public static CoordinateView of(int x, int y) {
        return new HiddenCoordinate(x, y);
    }

    public static int manhattanDistanceFromOrigin(CoordinateView coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        // Works with any implementation of the public contract.
        return Math.addExact(
                Math.absExact(coordinate.x()),
                Math.absExact(coordinate.y()));
    }
}

/** Package-private implementation can change independently of client source. */
final class HiddenCoordinate implements CoordinateView {

    private final int x;
    private final int y;

    HiddenCoordinate(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public int x() {
        return x;
    }

    @Override
    public int y() {
        return y;
    }
}
