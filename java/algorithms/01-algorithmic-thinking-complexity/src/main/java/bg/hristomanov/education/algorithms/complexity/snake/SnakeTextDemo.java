package bg.hristomanov.education.algorithms.complexity.snake;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Optional headless demo. The gameplay and tests do not depend on a GUI,
 * keyboard, Thread.sleep(), or process exit codes.
 */
public final class SnakeTextDemo {
    private SnakeTextDemo() {
    }

    public static void main(String[] args) {
        SnakeGame game = SnakeGame.newGame(7, 5, new Random(2026));
        List<SnakeGame.Direction> script = List.of(
                SnakeGame.Direction.RIGHT,
                SnakeGame.Direction.DOWN,
                SnakeGame.Direction.LEFT,
                SnakeGame.Direction.UP,
                SnakeGame.Direction.RIGHT,
                SnakeGame.Direction.DOWN);
        System.out.println(render(game.view()));
        for (SnakeGame.Direction move : script) {
            SnakeGame.Tick result = game.advance(move);
            System.out.println("MOVE " + move + " -> " + result.status()
                    + " ate=" + result.ate());
            System.out.println(render(game.view()));
            if (result.status() != SnakeGame.Status.RUNNING) {
                break;
            }
        }
    }

    public static String render(SnakeGame.View view) {
        char[][] grid = new char[view.height()][view.width()];
        for (char[] row : grid) {
            Arrays.fill(row, '.');
        }
        view.food().ifPresent(food -> grid[food.y()][food.x()] = '*');
        for (SnakeGame.Cell cell : view.snakeHeadFirst()) {
            grid[cell.y()][cell.x()] = 'o';
        }
        SnakeGame.Cell head = view.snakeHeadFirst().getFirst();
        grid[head.y()][head.x()] = '@';

        StringBuilder text = new StringBuilder();
        for (char[] row : grid) {
            text.append(row).append('\n');
        }
        return text.append("score=").append(view.score())
                .append(" status=").append(view.status()).toString();
    }
}
