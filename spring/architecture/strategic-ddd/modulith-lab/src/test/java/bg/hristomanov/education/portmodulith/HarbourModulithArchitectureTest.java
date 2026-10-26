package bg.hristomanov.education.portmodulith;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Turns package/API/named-interface decisions into an executable CI gate. */
class HarbourModulithArchitectureTest {

    @Test
    void modulesHaveNoCyclesAndCrossModuleImportsUseExplicitInterfaces() {
        ApplicationModules.of(HarbourModulithApplication.class).verify();
    }
}
