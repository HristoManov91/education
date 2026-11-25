package bg.hristomanov.education.consistency;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import java.nio.file.Path;
import java.util.List;
import static bg.hristomanov.education.consistency.DurableInbox.*;
import static org.junit.jupiter.api.Assertions.*;

class DurableInboxTest {
    @TempDir Path directory;
    private DurableInbox inbox;
    private static final long NOW = 10_000, BACKOFF = 100;

    @BeforeEach void setup() {
        inbox = reopen();
        inbox.register("order-1", 100);
        inbox.register("order-2", 100);
    }

    private DurableInbox reopen() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:file:" + directory.resolve("inbox").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE");
        ds.setUser("sa");
        var reopened = new DurableInbox(ds, 3, BACKOFF);
        reopened.initializeSchema();
        return reopened;
    }
    private Event event(String id, long seq, long observed, String delta) {
        return new Event(id, "order-1", seq, observed, delta);
    }
    private void assertAccount(long balance, long seq) { assertEquals(new Account(balance, seq), inbox.account("order-1")); }

    @Test void receiptRollbackDoesNotQualifyForAck() {
        var event = event("a",1, NOW,"10");
        assertThrows(RetryableFailure.class, () -> inbox.receive(event, NOW, () -> { throw new RetryableFailure("before commit"); }));
        assertEquals(0, reopen().count());
        assertAccount(100,0);
        assertEquals(Receipt.STORED, inbox.receive(event,NOW));
    }

    @Test void ackLossAndNewDatasourcePreservePayloadAndExactlyOneLocalEffect() {
        var event = event("a",1,NOW,"10");
        assertEquals(Receipt.STORED, inbox.receive(event,NOW));
        inbox = reopen(); // New datasource; receipt remains on disk after connections close.
        assertEquals(event, inbox.row("a").event());
        assertEquals(Receipt.DUPLICATE, inbox.receive(event,NOW+1));
        assertEquals(Result.APPLIED, inbox.process("a",NOW+1));
        assertEquals(Result.ALREADY_DONE, inbox.process("a",NOW+2));
        assertEquals(Receipt.DUPLICATE, inbox.receive(event,NOW+2));
        assertAccount(110,1);
        assertEquals(Status.DONE, reopen().row("a").status());
    }

    @Test void conflictingIdAndDifferentIdAtSameSequenceAreNotDuplicates() {
        inbox.receive(event("a",1,NOW,"10"),NOW);
        assertThrows(IllegalStateException.class, () -> inbox.receive(event("a",1,NOW,"99"),NOW));
        assertThrows(IllegalStateException.class, () -> inbox.receive(event("b",1,NOW,"10"),NOW));
        assertEquals(1,inbox.count());
        assertAccount(100,0);
    }

    @Test void unrelatedConstraintFailureIsNotAcknowledged() {
        assertThrows(DataIntegrityViolationException.class, () ->
                inbox.receive(new Event("a","missing-account",1,NOW,"10"),NOW));
        assertEquals(0,inbox.count());
    }

    @Test void effectAndVersionRollbackButRetryMetadataSurvives() {
        inbox.receive(event("a",1,NOW,"10"),NOW);
        var result = inbox.process("a",NOW,() -> { throw new RetryableFailure("after effect SQL"); });
        assertEquals(Result.RETRY_SCHEDULED,result);
        inbox = reopen();
        assertAccount(100,0);
        assertEquals(Status.PENDING,inbox.row("a").status());
        assertEquals(1,inbox.row("a").attempts());
        assertEquals(NOW+BACKOFF,inbox.row("a").dueAt());
        assertEquals(Result.NOT_DUE,inbox.process("a",NOW+BACKOFF-1));
        assertEquals(Result.APPLIED,inbox.process("a",NOW+BACKOFF));
        assertAccount(110,1);
        assertEquals(2,inbox.row("a").attempts());
    }

    @Test void gapWaitsDespiteEarlierTimestampAndDoesNotConsumeAttempt() {
        // v2 looks older by timestamp; its negative delta cannot run before v1.
        inbox.receive(event("v2",2,NOW-50,"-120"),NOW);
        assertEquals(Result.GAP,inbox.process("v2",NOW));
        assertAccount(100,0);
        assertEquals(0,inbox.row("v2").attempts());
        inbox.receive(event("v1",1,NOW+50,"50"),NOW);
        assertEquals(List.of("v1"),inbox.dueIds(NOW,10));
        assertEquals(Result.APPLIED,inbox.process("v1",NOW));
        assertAccount(150,1);
        assertEquals(Result.APPLIED,inbox.process("v2",NOW+BACKOFF));
        assertAccount(30,2);
    }

    @Test void boundedRetryExhaustionBlocksDependentDeltaAndExplicitRedriveRecovers() {
        inbox.receive(event("v1",1,NOW,"10"),NOW);
        inbox.receive(event("v2",2,NOW+1,"5"),NOW);
        for(int attempt=0; attempt<3; attempt++) {
            long tick = NOW+attempt*BACKOFF;
            assertEquals(attempt==2?Result.TERMINAL_FAILURE:Result.RETRY_SCHEDULED,
                    inbox.process("v1",tick,()->{throw new RetryableFailure("still unavailable");}));
        }
        assertEquals(Status.FAILED,inbox.row("v1").status());
        assertEquals(3,inbox.row("v1").attempts());
        assertAccount(100,0);
        assertEquals(Result.GAP,inbox.process("v2",NOW+2*BACKOFF));
        assertEquals(Result.TERMINAL_FAILURE,inbox.process("v1",NOW+3*BACKOFF));
        assertThrows(IllegalArgumentException.class,()->inbox.redrive("v1",NOW+3*BACKOFF,""));
        assertTrue(inbox.redrive("v1",NOW+3*BACKOFF,"INC-42 dependency fixed"));
        assertFalse(inbox.redrive("v1",NOW+3*BACKOFF,"duplicate operator command"));
        assertEquals(1,inbox.row("v1").redrives());
        assertEquals("INC-42 dependency fixed",inbox.row("v1").redriveReason());
        assertEquals(Result.APPLIED,inbox.process("v1",NOW+3*BACKOFF));
        assertEquals(Result.APPLIED,inbox.process("v2",NOW+3*BACKOFF));
        assertAccount(115,2);
    }

    @Test void permanentPoisonGoesTerminalWithoutRetryAndCannotBeEditedUnderSameId() {
        inbox.receive(event("v1",1,NOW,"not-a-number"),NOW);
        assertEquals(Result.TERMINAL_FAILURE,inbox.process("v1",NOW));
        assertEquals(1,inbox.row("v1").attempts());
        assertEquals(Status.FAILED,inbox.row("v1").status());
        assertTrue(inbox.dueIds(NOW+1000,10).isEmpty());
        assertThrows(IllegalStateException.class,()->inbox.receive(event("v1",1,NOW,"10"),NOW));
        assertAccount(100,0);
    }

    @Test void independentAggregateCanProgressWhileAnotherHasGap() {
        inbox.receive(event("gap",2,NOW,"5"),NOW);
        inbox.receive(new Event("other","order-2",1,NOW,"7"),NOW);
        assertEquals(List.of("gap"),inbox.dueIds(NOW,1));
        assertEquals(Result.GAP,inbox.process("gap",NOW));
        assertEquals(List.of("other"),inbox.dueIds(NOW,1));
        assertEquals(Result.APPLIED,inbox.process("other",NOW));
        assertEquals(new Account(107,1),inbox.account("order-2"));
        assertAccount(100,0);
    }
}
