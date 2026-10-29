package bg.hristomanov.education.bootifulsecurity;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHashTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void hashIsOneWayAndIncludesAlgorithmIdentifier() {
        String stored = encoder.encode("not-a-production-password");

        assertThat(stored).startsWith("{bcrypt}");
        assertThat(stored).doesNotContain("not-a-production-password");
        assertThat(encoder.matches("not-a-production-password", stored)).isTrue();
        assertThat(encoder.matches("wrong", stored)).isFalse();
    }

    @Test
    void separatelyEncodedPasswordsHaveDifferentSalts() {
        String first = encoder.encode("same-password");
        String second = encoder.encode("same-password");

        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches("same-password", first)).isTrue();
        assertThat(encoder.matches("same-password", second)).isTrue();
    }

    @Test
    void oldLowCostBcryptHashCanBeDetectedAndRehashedOnLogin() {
        String legacyHash = "{bcrypt}" + new BCryptPasswordEncoder(4).encode("known-secret");

        assertThat(encoder.matches("known-secret", legacyHash)).isTrue();
        assertThat(encoder.upgradeEncoding(legacyHash)).isTrue();

        // Only the actual login supplies the plaintext password.
        // Persisting the upgraded hash requires a UserDetailsPasswordService.
        String upgradedHash = encoder.encode("known-secret");
        assertThat(upgradedHash).startsWith("{bcrypt}");
        assertThat(encoder.upgradeEncoding(upgradedHash)).isFalse();
    }
}
