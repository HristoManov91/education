package bg.hristomanov.education.eval;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class EvalModel {
    private EvalModel() {}

    public record Session(String id, Instant start, Instant end) {
        public Session {
            Objects.requireNonNull(id);
            Objects.requireNonNull(start);
            Objects.requireNonNull(end);
            if (id.isBlank() || !start.isBefore(end)) throw new IllegalArgumentException("Invalid session");
        }
        public boolean overlaps(Session other) {
            return start.isBefore(other.end) && other.start.isBefore(end);
        }
    }

    // Фиксиран учебен corpus; synthetic facts, not a real conference calendar.
    public static final Map<String, Session> CATALOG = Map.of(
            "past", session("past", "09:00", "10:00"),
            "java", session("java", "10:00", "11:00"),
            "loom", session("loom", "10:30", "11:30"),
            "db", session("db", "11:00", "12:00"),
            "security", session("security", "12:00", "13:00"));

    private static Session session(String id, String start, String end) {
        return new Session(id, Instant.parse("2026-10-07T" + start + ":00Z"),
                Instant.parse("2026-10-07T" + end + ":00Z"));
    }

    public static final Map<String, Set<String>> FACTS_BY_DOC = Map.of(
            "venue-doc", Set.of("venue-hall-a"),
            "java-doc", Set.of("java-session"),
            "loom-doc", Set.of("loom-session"),
            "db-doc", Set.of("db-session"),
            "past-doc", Set.of("past-session"),
            "noise-doc", Set.of("coffee-break"));

    // Тези полета идват от наблюдавания tool boundary, НЕ от текста на assistant.
    public record ToolCall(String name, String sessionId, String targetOwner, boolean accepted) {
        public ToolCall {
            Objects.requireNonNull(name);
            Objects.requireNonNull(sessionId);
            Objects.requireNonNull(targetOwner);
        }
    }

    public record EvalCase(String id, String split, String slice, List<String> turns,
                           String now, String principal, List<String> initialSchedule,
                           Set<String> approvedAdds, Set<String> relevantDocs,
                           Set<String> expectedClaims, boolean expectedAbstention,
                           Set<String> expectedRecommendations, Set<String> expectedSchedule,
                           Set<String> allowedTools, List<ToolCall> requiredCalls,
                           int maxTurns, int maxCalls) {
        public EvalCase {
            Objects.requireNonNull(id); Objects.requireNonNull(split); Objects.requireNonNull(slice);
            Objects.requireNonNull(principal); Instant.parse(now);
            turns = List.copyOf(turns); initialSchedule = List.copyOf(initialSchedule);
            approvedAdds = Set.copyOf(approvedAdds); relevantDocs = Set.copyOf(relevantDocs);
            expectedClaims = Set.copyOf(expectedClaims); expectedRecommendations = Set.copyOf(expectedRecommendations);
            expectedSchedule = Set.copyOf(expectedSchedule); allowedTools = Set.copyOf(allowedTools);
            requiredCalls = List.copyOf(requiredCalls);
            if (id.isBlank() || principal.isBlank() || turns.isEmpty()
                    || maxTurns < turns.size() || maxCalls < requiredCalls.size() || maxCalls < 0
                    || !Set.of("dev", "holdout").contains(split)) {
                throw new IllegalArgumentException("Invalid case " + id);
            }
        }
    }

    public record Trace(String caseId, String variant, Set<String> retrievedDocs,
                        Set<String> claims, boolean abstained, List<String> recommendations,
                        List<ToolCall> calls, List<String> finalSchedule, int turns, double quality) {
        public Trace {
            Objects.requireNonNull(caseId); Objects.requireNonNull(variant);
            retrievedDocs = Set.copyOf(retrievedDocs); claims = Set.copyOf(claims);
            recommendations = List.copyOf(recommendations); calls = List.copyOf(calls);
            finalSchedule = List.copyOf(finalSchedule);
            if (turns < 0 || !Double.isFinite(quality) || quality < 0 || quality > 1)
                throw new IllegalArgumentException("Invalid trace");
        }
    }

    public record GoldenDataset(String version, List<EvalCase> cases) {
        public GoldenDataset {
            Objects.requireNonNull(version);
            cases = List.copyOf(cases);
            if (cases.isEmpty() || cases.stream().map(EvalCase::id).distinct().count() != cases.size())
                throw new IllegalArgumentException("Missing or duplicate case IDs");
        }
    }

    public record ReplayDataset(String version, List<Trace> traces) {
        public ReplayDataset {
            Objects.requireNonNull(version); traces = List.copyOf(traces);
        }
    }
}
