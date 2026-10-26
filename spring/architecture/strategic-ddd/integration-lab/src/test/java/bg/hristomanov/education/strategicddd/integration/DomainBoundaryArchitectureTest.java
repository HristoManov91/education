package bg.hristomanov.education.strategicddd.integration;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * CI catches coupling regression before it reaches production.
 * Independent Maven modules also keep domain artifacts from importing each other.
 */
class DomainBoundaryArchitectureTest {

    private static final String ROOT = "bg.hristomanov.education.strategicddd";

    private final JavaClasses classes = new ClassFileImporter().importPackages(ROOT);

    @Test
    void coreDomainsMustNotImportUpstreamModelsOrOtherDomainModels() {
        noClasses().that().resideInAPackage("..quoting..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..contracts..", "..claims..", "..integration..")
                .check(classes);

        noClasses().that().resideInAPackage("..contracts..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..quoting..", "..claims..", "..integration..")
                .check(classes);

        noClasses().that().resideInAPackage("..claims..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..quoting..", "..contracts..", "..integration..")
                .check(classes);
    }

    @Test
    void domainDecisionsMustRemainFrameworkFree() {
        noClasses().that().resideInAnyPackage(
                        "..quoting..", "..contracts..", "..claims..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..")
                .check(classes);
    }

    @Test
    void applicationBoundariesMustNotFormCycles() {
        slices().matching("bg.hristomanov.education.strategicddd.(*)..")
                .should().beFreeOfCycles().check(classes);
    }
}
