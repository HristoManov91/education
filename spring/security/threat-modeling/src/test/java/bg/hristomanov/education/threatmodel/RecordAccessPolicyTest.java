package bg.hristomanov.education.threatmodel;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

class RecordAccessPolicyTest {

    private final RecordAccessPolicy policy = new RecordAccessPolicy();

    @Test
    void missingOrAnonymousAuthenticationIsNotAValidOwner() {
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        assertThat(policy.canRead(null, "alice")).isFalse();
        assertThat(policy.canRead(anonymous, "anonymousUser")).isFalse();
    }

    @Test
    void anotherAuthenticatedUserCannotReadRecords() {
        assertThat(policy.canRead(actor("bob", "ROLE_USER"), "alice")).isFalse();
        assertThat(policy.canRead(actor("alice", "ROLE_USER"), "alice")).isTrue();
    }

    @Test
    void reviewerRoleAloneCannotApproveSomebodyElsesRecord() {
        assertThat(policy.canApprove(actor("bob", "ROLE_REVIEWER"), "alice")).isFalse();
    }

    @Test
    void ownershipAloneDoesNotGrantApproval() {
        assertThat(policy.canApprove(actor("alice", "ROLE_USER"), "alice")).isFalse();
        assertThat(policy.canApprove(actor("alice", "ROLE_REVIEWER"), "alice")).isTrue();
    }

    private static Authentication actor(String name, String role) {
        return new UsernamePasswordAuthenticationToken(
                name, null, List.of(new SimpleGrantedAuthority(role)));
    }
}
