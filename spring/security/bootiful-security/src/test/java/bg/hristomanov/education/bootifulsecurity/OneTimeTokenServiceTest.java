package bg.hristomanov.education.bootifulsecurity;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.InMemoryOneTimeTokenService;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

class OneTimeTokenServiceTest {

    @Test
    void generatedTokenCanBeConsumedOnlyOnce() {
        var service = new InMemoryOneTimeTokenService();
        OneTimeToken issued = service.generate(
                new GenerateOneTimeTokenRequest("alice", Duration.ofMinutes(5)));

        var submitted = new OneTimeTokenAuthenticationToken(issued.getTokenValue());

        assertThat(service.consume(submitted)).isNotNull()
                .extracting(OneTimeToken::getUsername).isEqualTo("alice");
        assertThat(service.consume(new OneTimeTokenAuthenticationToken(issued.getTokenValue())))
                .isNull();
    }

    @Test
    void arbitraryTokenIsNotAccepted() {
        var service = new InMemoryOneTimeTokenService();

        assertThat(service.consume(new OneTimeTokenAuthenticationToken("invalid-token")))
                .isNull();
    }
}
