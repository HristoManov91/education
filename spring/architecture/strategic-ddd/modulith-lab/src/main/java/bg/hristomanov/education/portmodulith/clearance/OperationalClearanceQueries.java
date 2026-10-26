package bg.hristomanov.education.portmodulith.clearance;

/** Read-only query contract. The internal state is not exposed to other modules. */
public interface OperationalClearanceQueries {

    ClearanceStatus statusOf(String vesselId);
}
