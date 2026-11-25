package bg.hristomanov.education.eval;

import java.util.*;

public final class EvalMetrics {
    private EvalMetrics() {}

    public record Retrieval(OptionalDouble precision, OptionalDouble recall) {}
    public static Retrieval retrieval(Set<String> retrieved, Set<String> relevant) {
        long hits = retrieved.stream().filter(relevant::contains).count();
        return new Retrieval(ratio(hits, retrieved.size()), ratio(hits, relevant.size()));
    }

    public record Confusion(int tp, int tn, int fp, int fn) {
        public OptionalDouble agreement() { return ratio(tp + tn, tp + tn + fp + fn); }
        public OptionalDouble falsePassRate() { return ratio(fp, fp + tn); }
    }
    public static Confusion confusion(List<Boolean> human, List<Boolean> judge) {
        if (human.size() != judge.size() || human.isEmpty()) throw new IllegalArgumentException("Paired labels required");
        int tp = 0, tn = 0, fp = 0, fn = 0;
        for (int i = 0; i < human.size(); i++) {
            boolean h = human.get(i), j = judge.get(i);
            if (h && j) tp++;
            else if (!h && !j) tn++;
            else if (!h) fp++;
            else fn++;
        }
        return new Confusion(tp, tn, fp, fn);
    }

    public static OptionalDouble sampleSd(List<Double> scores) {
        scores.forEach(EvalMetrics::requireScore);
        if (scores.size() < 2) return OptionalDouble.empty();
        double mean = scores.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double squares = scores.stream().mapToDouble(x -> (x - mean) * (x - mean)).sum();
        return OptionalDouble.of(Math.sqrt(squares / (scores.size() - 1)));
    }

    public record TrialRates(double perTrial, double anyPass, double allPass) {}
    public static TrialRates empiricalTrials(List<List<Boolean>> trials) {
        if (trials.isEmpty() || trials.getFirst().isEmpty()) throw new IllegalArgumentException("Trials required");
        int k = trials.getFirst().size();
        long pass = 0, any = 0, all = 0;
        for (var row : trials) {
            if (row.size() != k) throw new IllegalArgumentException("Same k required");
            long count = row.stream().mapToInt(x -> x ? 1 : 0).sum();
            pass += count;
            if (count > 0) any++;
            if (count == k) all++;
        }
        return new TrialRates((double) pass / ((long) trials.size() * k),
                (double) any / trials.size(), (double) all / trials.size());
    }

    public static long plannedCalls(long cases, long trials, long callsPerTrial) {
        if (cases < 1 || trials < 1 || callsPerTrial < 1) throw new IllegalArgumentException("Positive workload required");
        return Math.multiplyExact(Math.multiplyExact(cases, trials), callsPerTrial);
    }

    static void requireScore(double score) {
        if (!Double.isFinite(score) || score < 0 || score > 1) throw new IllegalArgumentException("Invalid score");
    }
    private static OptionalDouble ratio(long numerator, long denominator) {
        return denominator == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) numerator / denominator);
    }
}
