package bg.hristomanov.education.handles;

/** Immutable value; all dispatch variants must preserve the same arithmetic. */
public record Point(int x, int y) {

    public Point translate(int dx, int dy) {
        return new Point(Math.addExact(x, dx), Math.addExact(y, dy));
    }
}
