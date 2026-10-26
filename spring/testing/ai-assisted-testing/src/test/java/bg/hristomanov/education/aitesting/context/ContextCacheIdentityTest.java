package bg.hristomanov.education.aitesting.context;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests Spring TestContext's actual static cache within one JVM:
 * same MergedContextConfiguration -> same instance;
 * distinct @TestPropertySource -> distinct instance.
 */
class ContextCacheIdentityTest {

    @Configuration(proxyBeanMethods = false)
    static class DemoContext {
        @Bean
        String marker() {
            return "same-context";
        }
    }

    @ContextConfiguration(classes = DemoContext.class)
    static class FirstConsumer {
    }

    @ContextConfiguration(classes = DemoContext.class)
    static class SecondConsumer {
    }

    @ContextConfiguration(classes = DemoContext.class)
    @TestPropertySource(properties = "education.variant=changed")
    static class DifferentPropertiesConsumer {
    }

    @Test
    void identicalConfigurationReusesContextButDifferentPropertiesDoNot() {
        ApplicationContext first = new TestContextManager(FirstConsumer.class)
                .getTestContext().getApplicationContext();
        ApplicationContext second = new TestContextManager(SecondConsumer.class)
                .getTestContext().getApplicationContext();
        ApplicationContext different = new TestContextManager(DifferentPropertiesConsumer.class)
                .getTestContext().getApplicationContext();

        assertThat(second).isSameAs(first);
        assertThat(different).isNotSameAs(first);
        assertThat(first.getBean("marker")).isEqualTo("same-context");
    }
}
