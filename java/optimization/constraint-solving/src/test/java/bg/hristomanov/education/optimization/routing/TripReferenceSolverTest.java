package bg.hristomanov.education.optimization.routing;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TripReferenceSolverTest {

    @Test
    void mustVisitIsKeptAndOptionalStopsMustFitInTimeWindow() {
        TripReferenceSolver.Visit mandatory = new TripReferenceSolver.Visit(
                "DINNER", 2, 60, 600, 780, 10, true);
        TripReferenceSolver.Visit nearby = new TripReferenceSolver.Visit(
                "GARDEN", 3, 30, 600, 900, 5, false);
        TripReferenceSolver.Visit far = new TripReferenceSolver.Visit(
                "BEACH", 20, 100, 600, 900, 2, false);

        TripReferenceSolver.Plan result = TripReferenceSolver.optimize(
                List.of(mandatory, nearby, far), 540, 810, 0).orElseThrow();

        assertTrue(result.itinerary().contains(mandatory));
        assertTrue(result.itinerary().contains(nearby));
        assertFalse(result.itinerary().contains(far));
        assertTrue(result.finishMinute() <= 810);
    }

    @Test
    void mandatoryVisitOutsideItsWindowMakesThePlanImpossible() {
        TripReferenceSolver.Visit mustVisit = new TripReferenceSolver.Visit(
                "CLOSED", 3, 60, 400, 500, 10, true);
        assertTrue(TripReferenceSolver.optimize(
                List.of(mustVisit), 540, 900, 0).isEmpty());
    }

    @Test
    void twoOptionalStopsCompareUtilityAgainstTravelAndTime() {
        TripReferenceSolver.Visit close = new TripReferenceSolver.Visit(
                "CLOSE", 1, 30, 540, 900, 4, false);
        TripReferenceSolver.Visit distant = new TripReferenceSolver.Visit(
                "FAR", 20, 100, 540, 900, 1, false);
        TripReferenceSolver.Plan plan = TripReferenceSolver.optimize(
                List.of(close, distant), 540, 660, 0).orElseThrow();
        assertEquals(List.of(close), plan.itinerary());
        assertEquals(10, plan.totalTravelMinutes());
    }

    @Test
    void invalidInputsFailInsteadOfSearchingUnboundedProblemSpace() {
        assertThrows(IllegalArgumentException.class,
                () -> TripReferenceSolver.optimize(List.of(), 540, 540, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new TripReferenceSolver.Visit("X", 1, -1, 0, 100, 1, false));
        TripReferenceSolver.Visit duplicate = new TripReferenceSolver.Visit(
                "X", 0, 30, 540, 600, 1, false);
        assertThrows(IllegalArgumentException.class,
                () -> TripReferenceSolver.optimize(List.of(duplicate, duplicate), 540, 900, 0));
    }
}
