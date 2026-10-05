package bg.hristomanov.education.algorithms.graphalgorithms.shortestpath;

final class ShortestPathMath {

    static final long INFINITY = Long.MAX_VALUE / 4;

    private ShortestPathMath() {
    }

    static long add(long left, long right) {
        if (left == INFINITY) {
            return INFINITY;
        }

        return Math.addExact(left, right);
    }
}
