package bg.hristomanov.education.eval;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleToolTest {
    private ScheduleTool tool() {
        return new ScheduleTool(Instant.parse("2026-10-07T10:00:00Z"),
                Map.of("alice", List.of("java"), "bob", List.of("security")));
    }
    @Test
    void noCrossOwnerReadOrWrite() {
        var tool = tool();
        assertTrue(tool.read("alice", "bob").isEmpty());
        assertFalse(tool.add("alice", "bob", "db", Set.of("db")));
        assertEquals(List.of("security"), tool.read("bob", "bob").orElseThrow());
    }
    @Test
    void approvalRequiredEvenForAuthorizedOwner() {
        var tool = tool();
        assertFalse(tool.add("alice", "alice", "db", Set.of()));
        assertEquals(List.of("java"), tool.read("alice", "alice").orElseThrow());
    }
    @Test
    void overlappingAndPastAndUnknownSessionsAreRejected() {
        var tool = tool();
        assertFalse(tool.add("alice", "alice", "loom", Set.of("loom")));
        assertFalse(tool.add("alice", "alice", "past", Set.of("past")));
        assertFalse(tool.add("alice", "alice", "missing", Set.of("missing")));
        assertEquals(List.of("java"), tool.read("alice", "alice").orElseThrow());
    }
    @Test
    void adjacentSessionsAndDuplicateConfirmedAddAreAllowed() {
        var tool = tool();
        assertTrue(tool.add("alice", "alice", "db", Set.of("db")));
        assertTrue(tool.add("alice", "alice", "db", Set.of("db")));
        assertEquals(List.of("java", "db"), tool.read("alice", "alice").orElseThrow());
        assertEquals(List.of("security"), tool.read("bob", "bob").orElseThrow());
    }
    @Test
    void sessionAtNowIsAllowedButPastIsNot() {
        var tool = new ScheduleTool(Instant.parse("2026-10-07T10:00:00Z"), Map.of());
        assertTrue(tool.add("alice", "alice", "java", Set.of("java")));
        assertFalse(tool.add("alice", "alice", "past", Set.of("past")));
    }
}
