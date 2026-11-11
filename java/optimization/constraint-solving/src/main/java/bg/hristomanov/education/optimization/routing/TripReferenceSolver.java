package bg.hristomanov.education.optimization.routing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Tiny independent exhaustive reference for the lecture's honeymoon routing
 * idea. NOT Timefold @PlanningListVariable and NOT a scalable VRP engine.
 *
 * <p>Travel time is a deterministic zone-distance proxy (10 minutes per zone),
 * the activity can start after opening and must finish by closing and day end.
 */
public final class TripReferenceSolver {

    private static final int MAX_VISITS = 8;

    private TripReferenceSolver() {
    }

    public record Visit(String id, int zone, int durationMinutes,
                        int opensAtMinute, int closesAtMinute,
                        int desirability, boolean required) {

        public Visit {
            Objects.requireNonNull(id, "id");
            if (id.isBlank() || zone < 0 || zone > 20 || durationMinutes < 1
                    || opensAtMinute < 0 || closesAtMinute > 24 * 60
                    || closesAtMinute <= opensAtMinute
                    || desirability < 1 || desirability > 10) {
                throw new IllegalArgumentException("Invalid trip visit");
            }
        }
    }

    public record Plan(List<Visit> itinerary, int totalTravelMinutes,
                       int finishMinute, int utility) {

        public Plan {
            itinerary = List.copyOf(itinerary);
        }
    }

    public static Optional<Plan> optimize(List<Visit> input, int startMinute,
                                          int finishMinute, int startZone) {
        Objects.requireNonNull(input, "input");
        if (input.size() > MAX_VISITS || startMinute < 0 || finishMinute > 24 * 60
                || startMinute >= finishMinute || startZone < 0 || startZone > 20) {
            throw new IllegalArgumentException("Invalid reference search limits");
        }
        Set<String> ids = new HashSet<>();
        for (Visit visit : input) {
            if (!ids.add(visit.id())) {
                throw new IllegalArgumentException("Duplicate visit id");
            }
        }
        Search search = new Search(input, finishMinute);
        search.visit(new ArrayList<>(), new boolean[input.size()],
                startMinute, startZone, 0, 0);
        return Optional.ofNullable(search.best);
    }

    private static final class Search {
        private final List<Visit> input;
        private final int closingMinute;
        private Plan best;

        private Search(List<Visit> input, int closingMinute) {
            this.input = input;
            this.closingMinute = closingMinute;
        }

        private void visit(List<Visit> route, boolean[] used, int now,
                           int zone, int travelMinutes, int desirability) {
            boolean allRequired = true;
            for (int index = 0; index < input.size(); index++) {
                if (input.get(index).required() && !used[index]) {
                    allRequired = false;
                    break;
                }
            }
            if (allRequired) {
                int utility = desirability * 100 - travelMinutes;
                if (best == null || utility > best.utility()) {
                    best = new Plan(route, travelMinutes, now, utility);
                }
            }

            for (int index = 0; index < input.size(); index++) {
                if (used[index]) {
                    continue;
                }
                Visit next = input.get(index);
                int travel = Math.abs(zone - next.zone()) * 10;
                int arrival = now + travel;
                int begin = Math.max(arrival, next.opensAtMinute());
                int end = begin + next.durationMinutes();
                if (end > next.closesAtMinute() || end > closingMinute) {
                    continue;
                }
                used[index] = true;
                route.add(next);
                visit(route, used, end, next.zone(),
                        travelMinutes + travel, desirability + next.desirability());
                route.removeLast();
                used[index] = false;
            }
        }
    }
}
