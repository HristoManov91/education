package bg.hristomanov.education.aitesting.architecture;

import bg.hristomanov.education.aitesting.domain.RegistrationPolicy;
import com.enofex.taikai.ClassCriteria;
import com.enofex.taikai.Taikai;
import com.enofex.taikai.TaikaiRule;
import com.enofex.taikai.configures.Customizer;
import com.enofex.taikai.java.JavaConfigurer;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P2 architecture fitness functions: the real package is kept clean, while a
 * deliberately broken test-only fixture proves that the rules detect regressions.
 * No Spring context or infrastructure is required.
 */
class ArchitectureFitnessTest {

    private static final String ROOT = "bg.hristomanov.education.aitesting";

    // A small reusable team baseline. Do not enable every Taikai rule indiscriminately.
    private static final Customizer<JavaConfigurer> SHARED_JAVA_RULES = java -> java
            .fieldsShouldNotBePublic()
            .noUsageOfSystemOutOrErr()
            .imports(imports -> imports.shouldHaveNoCycles());

    @Test
    void realApplicationRespectsSharedAndProjectSpecificBoundaries() {
        Taikai.builder()
                .namespace(ROOT)
                .failOnEmpty(true)
                .java(SHARED_JAVA_RULES)
                .spring(spring -> spring
                        .noAutowiredFields()
                        .controllers(controllers -> controllers
                                .shouldNotDependOnRepositories()))
                .addRule(TaikaiRule.of(
                        noClasses().that().resideInAPackage("..domain..")
                                .should().dependOnClassesThat().resideInAnyPackage(
                                        "org.springframework..", "jakarta.persistence..")
                                .because("the registration policy must stay framework independent")))
                .build()
                .checkAll();
    }

    @Test
    void checkStopsEarlyWhileCheckAllReportsEveryBrokenRule() {
        // Explicit test-only class selection is intentional: no bad example
        // leaks into src/main or weakens the CI rule for production classes.
        Taikai deliberatelyBroken = Taikai.builder()
                .classes(DirectRepositoryController.class, DemoRepository.class)
                .java(java -> java.fieldsShouldNotBePublic())
                .spring(spring -> spring.controllers(controllers -> controllers
                        .shouldNotDependOnRepositories()))
                .build();

        AssertionError firstFailure = assertThrows(AssertionError.class, deliberatelyBroken::check);
        AssertionError aggregateFailure = assertThrows(AssertionError.class, deliberatelyBroken::checkAll);

        assertThat(firstFailure.getMessage())
                .contains("Fields should not be public unless they are static");
        assertThat(aggregateFailure.getMessage())
                .contains("Fields should not be public unless they are static")
                .contains("Controllers should not depend on Repositories")
                .contains("2 rules");
    }

    @Test
    void failOnEmptyPreventsSilentlyPassingMisspelledSelectors() {
        Taikai noMatchingClasses = Taikai.builder()
                .classes(RegistrationPolicy.class)
                .failOnEmpty(true)
                .java(java -> java.classesShouldBeRecords(
                        ClassCriteria.simpleNameMatching("CompletelyMissingType")))
                .build();

        assertThrows(AssertionError.class, noMatchingClasses::checkAll);
    }

    @Test
    void aNarrowDocumentedExceptionDoesNotHideOtherViolations() {
        // During a migration, exclude exactly one legacy class from one rule.
        // A second invalid controller must still fail the architectural gate.
        // With Taikai.builder().classes(...), Configuration.of(List.of(...))
        // is ignored: the global explicit classes take precedence. Bind the
        // excluded class to an explicit per-rule JavaClasses set instead.
        ClassFileImporter importer = new ClassFileImporter();
        JavaClasses legacyClasses = importer.importClasses(
                DirectRepositoryController.class, DemoRepository.class);
        var legacyException = TaikaiRule.Configuration.of(
                legacyClasses, java.util.List.of(DirectRepositoryController.class));

        Taikai legacyOnly = Taikai.builder()
                .classes(legacyClasses)
                .spring(spring -> spring.controllers(controllers -> controllers
                        .shouldNotDependOnRepositories(legacyException)))
                .build();
        assertDoesNotThrow(legacyOnly::checkAll);

        JavaClasses allFixtures = importer.importClasses(
                DirectRepositoryController.class, StillBrokenController.class, DemoRepository.class);
        var scopedException = TaikaiRule.Configuration.of(
                allFixtures, java.util.List.of(DirectRepositoryController.class));

        Taikai withAnotherViolation = Taikai.builder()
                .classes(allFixtures)
                .spring(spring -> spring.controllers(controllers -> controllers
                        .shouldNotDependOnRepositories(scopedException)))
                .build();
        AssertionError remaining = assertThrows(AssertionError.class, withAnotherViolation::checkAll);
        assertThat(remaining.getMessage()).contains("StillBrokenController");
    }

    @Repository
    static class DemoRepository {
    }

    @RestController
    static class DirectRepositoryController {

        public final DemoRepository repository;

        DirectRepositoryController(DemoRepository repository) {
            this.repository = repository;
        }
    }

    @RestController
    static class StillBrokenController {
        private final DemoRepository repository;

        StillBrokenController(DemoRepository repository) {
            this.repository = repository;
        }
    }
}
