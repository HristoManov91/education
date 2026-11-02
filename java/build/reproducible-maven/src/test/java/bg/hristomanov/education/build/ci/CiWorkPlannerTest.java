package bg.hristomanov.education.build.ci;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CiWorkPlannerTest {

    @Test
    void documentationOnlyPullRequestCanUseTheCheapPathInTheIllustrativePolicy() {
        assertThat(CiWorkPlanner.select(CiWorkPlanner.EventKind.PULL_REQUEST,
                List.of("README.md", "docs/learning/ROADMAP.md",
                        "java/build/reproducible-maven/CI-EFFICIENCY.md")))
                .containsExactly(CiWorkPlanner.Check.DOCUMENTATION);
    }

    @Test
    void sourceChangesTriggerAllRequiredNormalChecks() {
        Set<CiWorkPlanner.Check> plan = CiWorkPlanner.select(
                CiWorkPlanner.EventKind.PULL_REQUEST,
                List.of("java/build/reproducible-maven/src/main/java/Example.java"));

        assertThat(plan).containsExactlyInAnyOrder(
                CiWorkPlanner.Check.DOCUMENTATION,
                CiWorkPlanner.Check.COMPILE,
                CiWorkPlanner.Check.UNIT_TESTS,
                CiWorkPlanner.Check.INTEGRATION_TESTS);
    }

    @Test
    void buildConfigurationOrUnknownFilesMustFailClosed() {
        for (String file : List.of("pom.xml", ".github/workflows/verify.yml",
                ".mvn/maven.config", "scripts/check.sh", "docs/custom-script.sh",
                "unknown.file")) {
            assertThat(CiWorkPlanner.select(CiWorkPlanner.EventKind.PULL_REQUEST, List.of(file)))
                    .contains(CiWorkPlanner.Check.COMPILE,
                            CiWorkPlanner.Check.UNIT_TESTS,
                            CiWorkPlanner.Check.INTEGRATION_TESTS);
        }
    }

    @Test
    void emptyDiffMustNotSilentlySkipValidation() {
        assertThat(CiWorkPlanner.select(CiWorkPlanner.EventKind.PULL_REQUEST, List.of()))
                .contains(CiWorkPlanner.Check.COMPILE,
                        CiWorkPlanner.Check.UNIT_TESTS,
                        CiWorkPlanner.Check.INTEGRATION_TESTS);
    }

    @Test
    void mainAndReleaseNeverSkipRequiredChecksEvenForDocsOnlyChange() {
        List<String> docs = List.of("README.md");
        assertThat(CiWorkPlanner.select(CiWorkPlanner.EventKind.MAIN_PUSH, docs))
                .contains(CiWorkPlanner.Check.COMPILE,
                        CiWorkPlanner.Check.UNIT_TESTS,
                        CiWorkPlanner.Check.INTEGRATION_TESTS)
                .doesNotContain(CiWorkPlanner.Check.RELEASE_INTEGRITY);
        assertThat(CiWorkPlanner.select(CiWorkPlanner.EventKind.RELEASE, docs))
                .contains(CiWorkPlanner.Check.COMPILE,
                        CiWorkPlanner.Check.UNIT_TESTS,
                        CiWorkPlanner.Check.INTEGRATION_TESTS,
                        CiWorkPlanner.Check.RELEASE_INTEGRITY);
    }

    @Test
    void cancelOnlySupersededPullRequestWork() {
        assertThat(CiWorkPlanner.cancelSuperseded(CiWorkPlanner.EventKind.PULL_REQUEST)).isTrue();
        assertThat(CiWorkPlanner.cancelSuperseded(CiWorkPlanner.EventKind.MAIN_PUSH)).isFalse();
        assertThat(CiWorkPlanner.cancelSuperseded(CiWorkPlanner.EventKind.RELEASE)).isFalse();
    }

    @Test
    void identicalSourceAndToolchainAllowReuseButAnyMismatchRejectsIt() {
        CiWorkPlanner.ArtifactIdentity original =
                new CiWorkPlanner.ArtifactIdentity("commit-A", "JDK-25", "pom-sha-A");

        assertThat(CiWorkPlanner.mayReuse(original,
                new CiWorkPlanner.ArtifactIdentity("commit-A", "JDK-25", "pom-sha-A")))
                .isTrue();
        assertThat(CiWorkPlanner.mayReuse(original,
                new CiWorkPlanner.ArtifactIdentity("commit-B", "JDK-25", "pom-sha-A")))
                .isFalse();
        assertThat(CiWorkPlanner.mayReuse(original,
                new CiWorkPlanner.ArtifactIdentity("commit-A", "JDK-27", "pom-sha-A")))
                .isFalse();
        assertThat(CiWorkPlanner.mayReuse(original,
                new CiWorkPlanner.ArtifactIdentity("commit-A", "JDK-25", "pom-sha-B")))
                .isFalse();
    }

    @Test
    void badArtifactIdentityOrNullDiffIsRejectedInsteadOfAssumedSafe() {
        assertThatThrownBy(() -> new CiWorkPlanner.ArtifactIdentity(" ", "JDK-25", "digest"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CiWorkPlanner.select(
                CiWorkPlanner.EventKind.PULL_REQUEST, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CiWorkPlanner.mayReuse(null,
                new CiWorkPlanner.ArtifactIdentity("id", "jdk", "digest")))
                .isInstanceOf(NullPointerException.class);
        assertThat(CiWorkPlanner.select(CiWorkPlanner.EventKind.PULL_REQUEST,
                List.of("docs/../../unsafe.md")))
                .contains(CiWorkPlanner.Check.COMPILE);
    }

    @Test
    void aReturnedPlanCannotBeModifiedByTheCaller() {
        Set<CiWorkPlanner.Check> plan = CiWorkPlanner.select(
                CiWorkPlanner.EventKind.PULL_REQUEST, List.of("README.md"));
        assertThatThrownBy(() -> plan.add(CiWorkPlanner.Check.COMPILE))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
