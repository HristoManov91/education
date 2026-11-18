package bg.hristomanov.education.algorithms.complexity.snake;

import static bg.hristomanov.education.algorithms.complexity.snake.SnakeGame.Direction.*;
import static bg.hristomanov.education.algorithms.complexity.snake.SnakeGame.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SnakeGameTest {
    private static SnakeGame.Cell at(int x, int y) {
        return new SnakeGame.Cell(x, y);
    }

    private static SnakeGame game(int width, int height, SnakeGame.Cell food,
                                  SnakeGame.Cell... headFirst) {
        return SnakeGame.fromState(width, height, List.of(headFirst),
                RIGHT, food, new Random(19));
    }

    @Test
    void movingAddsHeadAndRemovesOnlyTail() {
        SnakeGame game = game(6, 3, at(5, 0), at(2, 1), at(1, 1), at(0, 1));

        SnakeGame.Tick tick = game.advance(RIGHT);

        assertEquals(new SnakeGame.Tick(RUNNING, true, false, false), tick);
        assertEquals(List.of(at(3, 1), at(2, 1), at(1, 1)),
                game.view().snakeHeadFirst());
        assertEquals(0, game.view().score());
    }

    @Test
    void aReverseCommandIsIgnoredBeforeMoving() {
        SnakeGame game = game(6, 3, at(5, 0), at(2, 1), at(1, 1), at(0, 1));

        SnakeGame.Tick tick = game.advance(LEFT);

        assertTrue(tick.reverseIgnored());
        assertEquals(RUNNING, tick.status());
        assertEquals(RIGHT, game.view().direction());
        assertEquals(at(3, 1), game.view().snakeHeadFirst().getFirst());
    }

    @Test
    void aWallIsNotAnImplicitRowWrap() {
        SnakeGame game = game(4, 3, at(0, 0), at(3, 1), at(2, 1), at(1, 1));
        SnakeGame.View before = game.view();

        SnakeGame.Tick collision = game.advance(RIGHT);

        assertEquals(LOST, collision.status());
        assertFalse(collision.moved());
        assertEquals(before.snakeHeadFirst(), game.view().snakeHeadFirst());
        assertEquals(before.food(), game.view().food());
        assertEquals(LOST, game.view().status());
    }

    @Test
    void hittingTheBodyIsTerminalWithoutChangingTheSnake() {
        SnakeGame game = game(5, 4, at(0, 0), at(2, 1), at(1, 1), at(1, 2),
                at(2, 2), at(3, 2), at(3, 1), at(3, 0));
        List<SnakeGame.Cell> before = game.view().snakeHeadFirst();

        assertEquals(LOST, game.advance(RIGHT).status());
        assertEquals(before, game.view().snakeHeadFirst());
        assertEquals(0, game.view().score());
    }

    @Test
    void enteringTheCellVacatedByTheTailIsLegalWhenNotEating() {
        SnakeGame game = game(4, 4, at(0, 0),
                at(2, 1), at(1, 1), at(1, 2), at(2, 2));

        SnakeGame.Tick move = game.advance(DOWN);

        assertEquals(RUNNING, move.status());
        assertTrue(move.moved());
        assertFalse(move.ate());
        assertEquals(List.of(at(2, 2), at(2, 1), at(1, 1), at(1, 2)),
                game.view().snakeHeadFirst());
        assertEquals(4, game.view().snakeHeadFirst().size());
    }

    @Test
    void eatingGrowsExactlyOneSegmentAndNeverSpawnsOnSnake() {
        SnakeGame game = game(5, 3, at(3, 1), at(2, 1), at(1, 1), at(0, 1));

        SnakeGame.Tick result = game.advance(RIGHT);

        assertEquals(new SnakeGame.Tick(RUNNING, true, true, false), result);
        assertEquals(4, game.view().snakeHeadFirst().size());
        assertEquals(1, game.view().score());
        assertTrue(game.view().food().isPresent());
        assertFalse(game.view().snakeHeadFirst().contains(game.view().food().orElseThrow()));
    }

    @Test
    void nearlyFullBoardHasABoundedFoodChoiceThenEndsInWin() {
        SnakeGame game = game(3, 2, at(2, 0),
                at(1, 0), at(0, 0), at(0, 1), at(1, 1));

        assertTrue(game.advance(RIGHT).ate());
        assertEquals(at(2, 1), game.view().food().orElseThrow());
        assertEquals(5, game.view().snakeHeadFirst().size());

        SnakeGame.Tick finalMove = game.advance(DOWN);
        assertEquals(WON, finalMove.status());
        assertTrue(finalMove.ate());
        assertEquals(2, game.view().score());
        assertEquals(6, game.view().snakeHeadFirst().size());
        assertTrue(game.view().food().isEmpty());
    }

    @Test
    void alreadyFullStarterBoardIsWonAndDoesNotAskRandomForFood() {
        SnakeGame game = SnakeGame.newGame(3, 1, new Random(5));

        assertEquals(WON, game.view().status());
        assertTrue(game.view().food().isEmpty());
        assertEquals(3, game.view().snakeHeadFirst().size());
        assertEquals(new SnakeGame.Tick(WON, false, false, false),
                game.advance(RIGHT));
    }

    @Test
    void terminalStateIsIdempotentAndCannotAccidentallyRestart() {
        SnakeGame game = game(4, 3, at(0, 0), at(3, 1), at(2, 1), at(1, 1));
        game.advance(RIGHT);
        SnakeGame.View terminal = game.view();

        assertEquals(new SnakeGame.Tick(LOST, false, false, false),
                game.advance(UP));
        assertEquals(terminal, game.view());
    }

    @Test
    void viewIsAnIndependentImmutableSnapshot() {
        SnakeGame game = game(5, 3, at(4, 0), at(2, 1), at(1, 1), at(0, 1));
        SnakeGame.View frozen = game.view();

        assertThrows(UnsupportedOperationException.class,
                () -> frozen.snakeHeadFirst().add(at(3, 1)));
        game.advance(RIGHT);

        assertEquals(at(2, 1), frozen.snakeHeadFirst().getFirst());
        assertEquals(at(3, 1), game.view().snakeHeadFirst().getFirst());
    }

    @Test
    void boardDimensionsAndInitialStateAreValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> SnakeGame.newGame(2, 4, new Random(0)));
        assertThrows(IllegalArgumentException.class,
                () -> SnakeGame.newGame(3, 0, new Random(0)));
        assertThrows(IllegalArgumentException.class,
                () -> SnakeGame.newGame(Integer.MAX_VALUE, 2, new Random(0)));
        assertThrows(IllegalArgumentException.class,
                () -> SnakeGame.newGame(1001, 1000, new Random(0)));

        assertThrows(IllegalArgumentException.class,
                () -> game(4, 3, at(0, 0), at(2, 1), at(1, 1), at(2, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> game(4, 3, at(0, 0), at(2, 1), at(0, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> game(4, 3, at(2, 1), at(2, 1), at(1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> SnakeGame.fromState(4, 3,
                        List.of(at(2, 1), at(1, 1)), LEFT, at(0, 0), new Random(0)));
        assertThrows(IllegalArgumentException.class,
                () -> SnakeGame.fromState(4, 3,
                        List.of(at(2, 1), at(1, 1)), RIGHT, null, new Random(0)));
    }

    @Test
    void deterministicRandomGeneratorReplaysTheSameGame() {
        SnakeGame a = SnakeGame.newGame(8, 6, new Random(1234));
        SnakeGame b = SnakeGame.newGame(8, 6, new Random(1234));
        assertEquals(a.view(), b.view());

        for (SnakeGame.Direction requested : List.of(RIGHT, DOWN, LEFT, UP, RIGHT, DOWN)) {
            assertEquals(a.advance(requested), b.advance(requested));
            assertEquals(a.view(), b.view());
        }
    }

    @Test
    void asciiRenderIsPureAndIncludesFoodAndStatus() {
        SnakeGame game = game(4, 3, at(3, 0), at(2, 1), at(1, 1), at(0, 1));
        String rendered = SnakeTextDemo.render(game.view());

        assertTrue(rendered.contains("...*"));
        assertTrue(rendered.contains("oo@."));
        assertTrue(rendered.contains("score=0 status=RUNNING"));
        assertEquals(game.view(), game.view());
    }
}
