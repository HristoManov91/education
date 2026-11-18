package bg.hristomanov.education.algorithms.complexity.snake;

import static bg.hristomanov.education.algorithms.complexity.snake.SnakeGame.Direction.*;
import static bg.hristomanov.education.algorithms.complexity.snake.SnakeGame.Status.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Independent, deliberately O(length) 2D oracle, not production code.
 * Tests compare the optimized head/tail + 1D-index game to a full list rebuild.
 */
class SnakeReferenceParityTest {
    private static SnakeGame.Cell at(int x, int y) {
        return new SnakeGame.Cell(x, y);
    }

    @Test
    void randomizedMovesAgreeWithAnIndependent2dReferenceOracle() {
        SnakeGame.Direction[] directions = { UP, RIGHT, DOWN, LEFT };
        int checkedTicks = 0;
        for (int scenario = 0; scenario < 300; scenario++) {
            long foodSeed = scenario + 10_000L;
            List<SnakeGame.Cell> initial = List.of(at(2, 2), at(1, 2), at(0, 2));
            SnakeGame.Cell initialFood = at(4, 2);
            SnakeGame optimized = SnakeGame.fromState(6, 5, initial, RIGHT, initialFood,
                    new Random(foodSeed));
            Reference2d naive = new Reference2d(6, 5, initial, RIGHT, initialFood,
                    new Random(foodSeed));
            Random moves = new Random(scenario * 71L + 19);

            assertEquals(naive.view(), optimized.view());
            for (int tick = 0; tick < 50; tick++) {
                SnakeGame.Direction requested = directions[moves.nextInt(directions.length)];
                SnakeGame.Tick actual = optimized.advance(requested);
                SnakeGame.Tick expected = naive.advance(requested);

                assertEquals(expected, actual, "tick " + tick + ", scenario " + scenario);
                assertEquals(naive.view(), optimized.view(),
                        "state after tick " + tick + ", scenario " + scenario);
                checkedTicks++;
                if (actual.status() != RUNNING) {
                    break;
                }
            }
        }
        // Guard that the test performs substantial replay, not just setup.
        org.junit.jupiter.api.Assertions.assertTrue(checkedTicks >= 300);
    }

    @Test
    void intentionalEatingAndTailVacatingAlsoAgreeWith2dReference() {
        List<SnakeGame.Cell> body = List.of(
                at(2, 1), at(1, 1), at(1, 2), at(2, 2));
        SnakeGame.Cell food = at(3, 1);
        SnakeGame optimized = SnakeGame.fromState(5, 4, body, RIGHT, food, new Random(7));
        Reference2d naive = new Reference2d(5, 4, body, RIGHT, food, new Random(7));

        for (SnakeGame.Direction requested : List.of(RIGHT, DOWN, LEFT, UP)) {
            assertEquals(naive.advance(requested), optimized.advance(requested));
            assertEquals(naive.view(), optimized.view());
        }
    }

    /**
     * Intentionally naive: store 2D cells, copy all positions per tick and
     * scan the entire list to test collisions and place food.
     */
    private static final class Reference2d {
        private final int width;
        private final int height;
        private final Random random;
        private List<SnakeGame.Cell> body;
        private SnakeGame.Direction direction;
        private SnakeGame.Cell food;
        private SnakeGame.Status status = RUNNING;
        private int score;

        private Reference2d(int width, int height, List<SnakeGame.Cell> initial,
                            SnakeGame.Direction direction, SnakeGame.Cell food, Random random) {
            this.width = width;
            this.height = height;
            this.body = new ArrayList<>(initial);
            this.direction = direction;
            this.food = food;
            this.random = random;
        }

        private SnakeGame.Tick advance(SnakeGame.Direction requested) {
            if (status != RUNNING) {
                return new SnakeGame.Tick(status, false, false, false);
            }
            boolean reverse = body.size() > 1 && isOpposite(direction, requested);
            SnakeGame.Direction step = reverse ? direction : requested;

            SnakeGame.Cell oldHead = body.getFirst();
            int nx = oldHead.x() + dx(step);
            int ny = oldHead.y() + dy(step);
            if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                status = LOST;
                return new SnakeGame.Tick(status, false, false, reverse);
            }

            SnakeGame.Cell next = at(nx, ny);
            boolean eating = next.equals(food);
            SnakeGame.Cell oldTail = body.getLast();
            if (body.contains(next) && (eating || !next.equals(oldTail))) {
                status = LOST;
                return new SnakeGame.Tick(status, false, false, reverse);
            }

            // This deliberately shifts/copies every segment, unlike SnakeGame.
            List<SnakeGame.Cell> newBody = new ArrayList<>();
            newBody.add(next);
            int previousLimit = body.size() - (eating ? 0 : 1);
            for (int index = 0; index < previousLimit; index++) {
                newBody.add(body.get(index));
            }
            body = newBody;
            direction = step;

            if (eating) {
                score++;
                if (body.size() == width * height) {
                    status = WON;
                    food = null;
                } else {
                    List<SnakeGame.Cell> empty = new ArrayList<>();
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            SnakeGame.Cell cell = at(x, y);
                            if (!body.contains(cell)) {
                                empty.add(cell);
                            }
                        }
                    }
                    food = empty.get(random.nextInt(empty.size()));
                }
            }
            return new SnakeGame.Tick(status, true, eating, reverse);
        }

        private SnakeGame.View view() {
            return new SnakeGame.View(width, height, body, direction,
                    Optional.ofNullable(food), score, status);
        }

        private static boolean isOpposite(SnakeGame.Direction a, SnakeGame.Direction b) {
            return dx(a) + dx(b) == 0 && dy(a) + dy(b) == 0;
        }

        private static int dx(SnakeGame.Direction direction) {
            return switch (direction) {
                case LEFT -> -1;
                case RIGHT -> 1;
                case UP, DOWN -> 0;
            };
        }

        private static int dy(SnakeGame.Direction direction) {
            return switch (direction) {
                case UP -> -1;
                case DOWN -> 1;
                case LEFT, RIGHT -> 0;
            };
        }
    }
}
