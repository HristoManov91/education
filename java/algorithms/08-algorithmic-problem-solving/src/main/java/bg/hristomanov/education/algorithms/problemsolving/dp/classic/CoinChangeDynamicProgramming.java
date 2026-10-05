package bg.hristomanov.education.algorithms.problemsolving.dp.classic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Един и същ input, две различни objectives:
 * minimum coins vs number of combinations.
 */
public final class CoinChangeDynamicProgramming {

    public MinimumCoinsResult minimumCoins(int[] coins, int amount) {
        validate(coins, amount);

        int unreachable = amount + 1;
        int[] minimum = new int[amount + 1];
        int[] chosenCoin = new int[amount + 1];
        Arrays.fill(minimum, unreachable);
        Arrays.fill(chosenCoin, -1);
        minimum[0] = 0;

        long transitionChecks = 0;

        for (int currentAmount = 1; currentAmount <= amount; currentAmount++) {
            for (int coin : coins) {
                transitionChecks++;

                if (coin <= currentAmount
                        && minimum[currentAmount - coin] != unreachable
                        && minimum[currentAmount - coin] + 1 < minimum[currentAmount]) {
                    minimum[currentAmount] = minimum[currentAmount - coin] + 1;
                    chosenCoin[currentAmount] = coin;
                }
            }
        }

        if (minimum[amount] == unreachable) {
            return new MinimumCoinsResult(-1, List.of(), transitionChecks);
        }

        List<Integer> usedCoins = new ArrayList<>();
        int remaining = amount;

        while (remaining > 0) {
            int coin = chosenCoin[remaining];
            usedCoins.add(coin);
            remaining -= coin;
        }

        return new MinimumCoinsResult(
                minimum[amount],
                List.copyOf(usedCoins),
                transitionChecks
        );
    }

    public CountWaysResult countCombinations(int[] coins, int amount) {
        validate(coins, amount);

        long[] ways = new long[amount + 1];
        ways[0] = 1;
        long transitionChecks = 0;

        // Coin-first order брои combinations, не permutations.
        int[] sortedCoins = coins.clone();
        Arrays.sort(sortedCoins);

        for (int coin : sortedCoins) {
            for (int currentAmount = coin; currentAmount <= amount; currentAmount++) {
                ways[currentAmount] += ways[currentAmount - coin];
                transitionChecks++;
            }
        }

        return new CountWaysResult(ways[amount], transitionChecks);
    }

    private void validate(int[] coins, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }

        for (int coin : coins) {
            if (coin <= 0) {
                throw new IllegalArgumentException("coin values must be > 0");
            }
        }
    }

    public record MinimumCoinsResult(
            int minimumCoinCount,
            List<Integer> coins,
            long transitionChecks
    ) {
    }

    public record CountWaysResult(
            long ways,
            long transitionChecks
    ) {
    }
}
