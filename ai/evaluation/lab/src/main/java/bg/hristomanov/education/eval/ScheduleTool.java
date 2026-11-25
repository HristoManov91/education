package bg.hristomanov.education.eval;

import java.time.Instant;
import java.util.*;

import static bg.hristomanov.education.eval.EvalModel.CATALOG;

/** Синтетичен stateful tool. Principal/approvals са trusted inputs от host, не от LLM. */
public final class ScheduleTool {
    private final Map<String, LinkedHashSet<String>> schedules = new HashMap<>();
    private final Instant now;

    public ScheduleTool(Instant now, Map<String, List<String>> initial) {
        this.now = Objects.requireNonNull(now);
        initial.forEach((owner, ids) -> {
            var set = new LinkedHashSet<>(ids);
            if (set.size() != ids.size() || !CATALOG.keySet().containsAll(set))
                throw new IllegalArgumentException("Invalid initial schedule");
            schedules.put(owner, set);
        });
    }

    public synchronized Optional<List<String>> read(String principal, String target) {
        if (principal == null || principal.isBlank() || !principal.equals(target)) return Optional.empty();
        return Optional.of(List.copyOf(schedules.getOrDefault(target, new LinkedHashSet<>())));
    }

    public synchronized boolean add(String principal, String target, String sessionId, Set<String> confirmedIds) {
        if (principal == null || principal.isBlank() || !principal.equals(target)
                || !confirmedIds.contains(sessionId)) return false;
        var candidate = CATALOG.get(sessionId);
        if (candidate == null || candidate.start().isBefore(now)) return false;
        var current = schedules.getOrDefault(target, new LinkedHashSet<>());
        if (current.contains(sessionId)) return true; // idempotent duplicate request
        if (current.stream().map(CATALOG::get).anyMatch(candidate::overlaps)) return false;
        schedules.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(sessionId);
        return true;
    }
}
