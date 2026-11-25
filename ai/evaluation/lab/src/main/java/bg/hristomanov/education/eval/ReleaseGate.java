package bg.hristomanov.education.eval;

import java.util.*;

public final class ReleaseGate {
    private ReleaseGate() {}

    public record Measurement(String caseId, boolean hardPass, Double quality) {}
    public record Decision(List<String> reasons) {
        public Decision { reasons = List.copyOf(reasons); }
        public boolean allowed() { return reasons.isEmpty(); }
    }

    /** One measured result per required case. Repeated trials must be summarized by a declared policy first. */
    public static Decision decide(Map<String, String> requiredCaseSlices, List<Measurement> measured,
                                  Map<String, Double> baselineBySlice, double floor, double tolerance,
                                  int minCasesPerSlice, long callsUsed, long callBudget) {
        EvalMetrics.requireScore(floor); EvalMetrics.requireScore(tolerance);
        if (requiredCaseSlices.isEmpty() || minCasesPerSlice < 1 || callsUsed < 0 || callBudget < 0)
            throw new IllegalArgumentException("Invalid gate configuration");
        var reasons = new ArrayList<String>();
        var byId = new HashMap<String, Measurement>();
        for (var m : measured) {
            if (!requiredCaseSlices.containsKey(m.caseId())) reasons.add("UNEXPECTED:" + m.caseId());
            if (byId.putIfAbsent(m.caseId(), m) != null) reasons.add("DUPLICATE:" + m.caseId());
        }
        if (callsUsed > callBudget) reasons.add("BUDGET");
        var slices = new HashMap<String, List<Double>>();
        requiredCaseSlices.forEach((id, slice) -> {
            var scores = slices.computeIfAbsent(slice, ignored -> new ArrayList<>());
            var m = byId.get(id);
            if (m == null) { reasons.add("MISSING:" + id); return; }
            if (!m.hardPass()) reasons.add("HARD:" + id);
            var score = m.quality();
            if (score == null || !Double.isFinite(score) || score < 0 || score > 1) {
                reasons.add("INVALID_SCORE:" + id);
            } else scores.add(score);
        });
        slices.forEach((slice, scores) -> {
            var baseline = baselineBySlice.get(slice);
            if (baseline == null || !Double.isFinite(baseline) || baseline < 0 || baseline > 1) {
                reasons.add("MISSING_OR_INVALID_BASELINE:" + slice);
            }
            if (scores.size() < minCasesPerSlice) reasons.add("INSUFFICIENT:" + slice);
            if (scores.isEmpty()) return;
            double average = scores.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            if (average < floor) reasons.add("FLOOR:" + slice);
            if (baseline != null && Double.isFinite(baseline) && average + tolerance + 1e-12 < baseline)
                reasons.add("REGRESSION:" + slice);
        });
        return new Decision(reasons);
    }
}
