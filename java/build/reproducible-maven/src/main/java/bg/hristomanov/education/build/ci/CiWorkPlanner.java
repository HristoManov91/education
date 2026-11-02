package bg.hristomanov.education.build.ci;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Educational decision policy for selecting CI checks. It is not wired into
 * the repository's required GitHub Actions Verify workflow.
 *
 * Unknown paths and empty diffs deliberately trigger all normal checks:
 * saving runner minutes must never silently remove correctness coverage.
 */
public final class CiWorkPlanner {

    private static final Set<Check> FULL_CHECKS =
            Set.of(Check.DOCUMENTATION, Check.COMPILE, Check.UNIT_TESTS,
                    Check.INTEGRATION_TESTS);

    private CiWorkPlanner() {
    }

    public enum EventKind {
        PULL_REQUEST, MAIN_PUSH, RELEASE
    }

    public enum Check {
        DOCUMENTATION, COMPILE, UNIT_TESTS, INTEGRATION_TESTS, RELEASE_INTEGRITY
    }

    public static Set<Check> select(EventKind event, List<String> changedPaths) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(changedPaths, "changedPaths");

        if (event == EventKind.RELEASE) {
            return Set.of(Check.DOCUMENTATION, Check.COMPILE, Check.UNIT_TESTS,
                    Check.INTEGRATION_TESTS, Check.RELEASE_INTEGRITY);
        }
        if (event == EventKind.MAIN_PUSH) {
            return FULL_CHECKS;
        }
        if (!changedPaths.isEmpty() && changedPaths.stream().allMatch(CiWorkPlanner::isDocumentation)) {
            return Set.of(Check.DOCUMENTATION);
        }
        // Includes source, POM, workflows, scripts, new/unknown paths and empty diffs.
        return FULL_CHECKS;
    }

    /**
     * Only superseded PR checks are safe to cancel.
     * A release or main-line integrity run must not be silently interrupted.
     */
    public static boolean cancelSuperseded(EventKind event) {
        return Objects.requireNonNull(event, "event") == EventKind.PULL_REQUEST;
    }

    /**
     * A reusable artifact belongs to exactly one source/toolchain identity.
     * This is a model of the checks performed by ci/compiled-artifact.sh.
     */
    public static boolean mayReuse(ArtifactIdentity expected, ArtifactIdentity downloaded) {
        return Objects.requireNonNull(expected, "expected")
                .equals(Objects.requireNonNull(downloaded, "downloaded"));
    }

    public record ArtifactIdentity(String commitSha, String jdkVersion, String pomSha256) {
        public ArtifactIdentity {
            requireNonBlank(commitSha, "commitSha");
            requireNonBlank(jdkVersion, "jdkVersion");
            requireNonBlank(pomSha256, "pomSha256");
        }

        private static void requireNonBlank(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
        }
    }

    private static boolean isDocumentation(String path) {
        if (path == null || path.isBlank() || path.contains("..")
                || path.startsWith("/") || path.contains("\\")) {
            return false;
        }
        if (path.equals("README.md")) {
            return true;
        }
        if (!path.endsWith(".md")) {
            return false;
        }
        return path.startsWith("docs/") || path.startsWith("java/")
                || path.startsWith("spring/") || path.startsWith("persistence/")
                || path.startsWith("design-patterns/") || path.startsWith("ai/");
    }
}
