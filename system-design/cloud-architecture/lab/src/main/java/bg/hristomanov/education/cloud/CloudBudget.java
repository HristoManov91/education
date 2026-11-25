package bg.hristomanov.education.cloud;

import java.math.BigDecimal;
import java.util.Objects;

public final class CloudBudget {
    private CloudBudget() {}

    public record Bounds(double lower, double upper) {}

    public static double independentMandatory(double first, double second) {
        requireProbability(first);
        requireProbability(second);
        return first * second;
    }

    public static Bounds mandatoryBounds(double first, double second) {
        requireProbability(first);
        requireProbability(second);
        return new Bounds(Math.max(0, first + second - 1), Math.min(first, second));
    }

    public static double independentRedundant(double first, double second) {
        requireProbability(first);
        requireProbability(second);
        return 1 - (1 - first) * (1 - second);
    }

    private static void requireProbability(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException("Probability must be between 0 and 1");
        }
    }

    public record Traffic(long requestBytes, long responseBytes) {
        public Traffic {
            if (requestBytes < 0 || responseBytes < 0) throw new IllegalArgumentException("Negative bytes");
        }
        public BigDecimal requestGB() { return BigDecimal.valueOf(requestBytes, 9); }
        public BigDecimal responseGB() { return BigDecimal.valueOf(responseBytes, 9); }
        public BigDecimal totalGB() { return requestGB().add(responseGB()); }
    }

    public static Traffic traffic(long callsPerSecond, long seconds, long requestBytes, long responseBytes) {
        if (callsPerSecond < 0 || seconds < 0 || requestBytes < 0 || responseBytes < 0) {
            throw new IllegalArgumentException("Non-negative input required");
        }
        long calls = Math.multiplyExact(callsPerSecond, seconds);
        return new Traffic(Math.multiplyExact(calls, requestBytes), Math.multiplyExact(calls, responseBytes));
    }

    public static BigDecimal cost(Traffic traffic, BigDecimal requestRate, BigDecimal responseRate,
                                  BigDecimal fixedCost) {
        Objects.requireNonNull(traffic);
        for (var price : new BigDecimal[]{requestRate, responseRate, fixedCost}) {
            if (price.signum() < 0) throw new IllegalArgumentException("Negative price");
        }
        return traffic.requestGB().multiply(requestRate)
                .add(traffic.responseGB().multiply(responseRate)).add(fixedCost);
    }
}
