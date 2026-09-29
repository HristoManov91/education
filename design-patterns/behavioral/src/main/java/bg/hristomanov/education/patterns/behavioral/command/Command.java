package bg.hristomanov.education.patterns.behavioral.command;

/**
 * Command капсулира action + необходимите данни в object.
 *
 * <p>Така action-ът може да бъде подаден към queue, scheduler, retry layer,
 * audit log или undo/history механизъм, вместо caller-ът да знае receiver API-то.</p>
 */
public interface Command<R> {

    String name();

    R execute();
}
