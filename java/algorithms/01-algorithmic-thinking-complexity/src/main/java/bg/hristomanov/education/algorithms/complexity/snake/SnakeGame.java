package bg.hristomanov.education.algorithms.complexity.snake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Headless Snake state machine: head-first deque + occupied-cell set.
 *
 * A normal non-eating move changes only the head and tail (expected O(1)
 * for deque/set operations). Food selection is a bounded O(width*height)
 * scan. No UI, sleeps, unbounded rejection sampling or process exit calls.
 *
 * Intentionally NOT thread-safe: the caller owns one instance/game loop.
 */
public final class SnakeGame {
    public enum Status { RUNNING, LOST, WON }

    public enum Direction {
        UP(0, -1), RIGHT(1, 0), DOWN(0, 1), LEFT(-1, 0);

        private final int dx;
        private final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        boolean isOpposite(Direction other) {
            return dx + other.dx == 0 && dy + other.dy == 0;
        }
    }

    public record Cell(int x, int y) { }

    /** Immutable snapshot: safe for UI, tests or replay outside this game. */
    public record View(int width, int height, List<Cell> snakeHeadFirst,
                       Direction direction, Optional<Cell> food, int score, Status status) {
        public View {
            snakeHeadFirst = List.copyOf(snakeHeadFirst);
            Objects.requireNonNull(direction);
            Objects.requireNonNull(food);
            Objects.requireNonNull(status);
        }
    }

    /** A terminal tick does not mutate the board or increment the score. */
    public record Tick(Status status, boolean moved, boolean ate, boolean reverseIgnored) { }

    private static final int MAX_CELLS = 1_000_000;

    private final int width;
    private final int height;
    private final int totalCells;
    private final Deque<Integer> body = new ArrayDeque<>();
    private final Set<Integer> occupied = new HashSet<>();
    private final RandomGenerator random;
    private Direction direction;
    private Status status;
    private int foodIndex = -1;
    private int score;

    private SnakeGame(int width, int height, List<Cell> initial, Direction direction,
                      Cell food, RandomGenerator random, boolean generateInitialFood) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Board dimensions must be positive");
        }
        try {
            totalCells = Math.multiplyExact(width, height);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Board dimensions overflow int", overflow);
        }
        if (totalCells > MAX_CELLS) {
            throw new IllegalArgumentException("Board exceeds the educational safety limit");
        }
        this.width = width;
        this.height = height;
        this.direction = Objects.requireNonNull(direction, "direction");
        this.random = Objects.requireNonNull(random, "random");
        Objects.requireNonNull(initial, "initial snake");
        if (initial.isEmpty() || initial.size() > totalCells) {
            throw new IllegalArgumentException("Snake size must be between 1 and board area");
        }

        Cell previous = null;
        for (Cell cell : initial) {
            Objects.requireNonNull(cell, "snake cell");
            int index = checkedIndex(cell);
            if (!occupied.add(index)) {
                throw new IllegalArgumentException("The snake must not intersect itself");
            }
            if (previous != null && Math.abs(previous.x() - cell.x())
                    + Math.abs(previous.y() - cell.y()) != 1) {
                throw new IllegalArgumentException("Snake body must contain adjacent cells");
            }
            body.addLast(index);
            previous = cell;
        }
        if (initial.size() > 1) {
            Cell head = initial.get(0);
            Cell neck = initial.get(1);
            if (head.x() - neck.x() != direction.dx || head.y() - neck.y() != direction.dy) {
                throw new IllegalArgumentException("Heading must point away from the neck");
            }
        }

        if (body.size() == totalCells) {
            if (food != null) {
                throw new IllegalArgumentException("A full board cannot contain food");
            }
            status = Status.WON;
        } else {
            status = Status.RUNNING;
            if (generateInitialFood) {
                foodIndex = chooseFood();
            } else {
                if (food == null) {
                    throw new IllegalArgumentException("A running game needs food");
                }
                foodIndex = checkedIndex(food);
                if (occupied.contains(foodIndex)) {
                    throw new IllegalArgumentException("Food must not overlap the snake");
                }
            }
        }
    }

    /** A three-cell snake starts facing right; food is reproducible for a seeded RNG. */
    public static SnakeGame newGame(int width, int height, RandomGenerator random) {
        if (width < 3) {
            throw new IllegalArgumentException("The starter snake needs at least three columns");
        }
        int row = height / 2;
        return new SnakeGame(width, height,
                List.of(new Cell(2, row), new Cell(1, row), new Cell(0, row)),
                Direction.RIGHT, null, random, true);
    }

    /**
     * Construct a valid scenario for deterministic replay/tests.
     * Head first; each adjacent pair must share an edge. If full, food must be null.
     */
    public static SnakeGame fromState(int width, int height, List<Cell> snakeHeadFirst,
                                      Direction heading, Cell food, RandomGenerator random) {
        return new SnakeGame(width, height, snakeHeadFirst, heading, food, random, false);
    }

    /**
     * Exactly one logical game tick. A requested 180-degree reversal is ignored
     * when the snake has two or more cells; the current heading is used instead.
     */
    public Tick advance(Direction requested) {
        Objects.requireNonNull(requested, "requested");
        if (status != Status.RUNNING) {
            return new Tick(status, false, false, false);
        }
        boolean reverseIgnored = body.size() > 1 && requested.isOpposite(direction);
        Direction stepDirection = reverseIgnored ? direction : requested;

        Cell head = toCell(body.getFirst());
        int x = head.x() + stepDirection.dx;
        int y = head.y() + stepDirection.dy;
        if (x < 0 || x >= width || y < 0 || y >= height) {
            status = Status.LOST;
            return new Tick(status, false, false, reverseIgnored);
        }

        int next = y * width + x;
        boolean eating = next == foodIndex;
        int tail = body.getLast();
        // Moving into the departing tail is legal, but NOT when eating (tail stays).
        if (occupied.contains(next) && (eating || next != tail)) {
            status = Status.LOST;
            return new Tick(status, false, false, reverseIgnored);
        }

        if (!eating) {
            occupied.remove(body.removeLast());
        }
        body.addFirst(next);
        occupied.add(next);
        direction = stepDirection;

        if (eating) {
            score++;
            if (occupied.size() == totalCells) {
                foodIndex = -1;
                status = Status.WON;
            } else {
                foodIndex = chooseFood();
            }
        }
        return new Tick(status, true, eating, reverseIgnored);
    }

    public View view() {
        List<Cell> cells = new ArrayList<>(body.size());
        for (int index : body) {
            cells.add(toCell(index));
        }
        return new View(width, height, cells, direction,
                foodIndex == -1 ? Optional.empty() : Optional.of(toCell(foodIndex)),
                score, status);
    }

    private int checkedIndex(Cell cell) {
        if (cell.x() < 0 || cell.x() >= width || cell.y() < 0 || cell.y() >= height) {
            throw new IllegalArgumentException("Cell is outside the board: " + cell);
        }
        return cell.y() * width + cell.x();
    }

    private Cell toCell(int index) {
        return new Cell(index % width, index / width);
    }

    /**
     * Uniformly choose the k-th unoccupied cell in row-major order.
     * The finite scan also handles a nearly full board without a retry loop.
     */
    private int chooseFood() {
        int free = totalCells - occupied.size();
        if (free <= 0) {
            throw new IllegalStateException("Cannot place food on a full board");
        }
        int choice = random.nextInt(free);
        for (int index = 0; index < totalCells; index++) {
            if (!occupied.contains(index) && choice-- == 0) {
                return index;
            }
        }
        throw new IllegalStateException("Occupied set violates board invariants");
    }
}
