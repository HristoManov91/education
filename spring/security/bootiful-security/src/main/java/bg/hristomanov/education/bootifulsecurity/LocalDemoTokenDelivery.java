package bg.hristomanov.education.bootifulsecurity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;
import org.springframework.security.web.authentication.ott.RedirectOneTimeTokenGenerationSuccessHandler;

@Configuration(proxyBeanMethods = false)
class LocalDemoTokenDelivery {

    @Bean
    @Profile("local-demo")
    OneTimeTokenGenerationSuccessHandler consoleOnlyOneTimeTokenDelivery() {
        var redirect = new RedirectOneTimeTokenGenerationSuccessHandler("/ott/sent");
        return (request, response, token) -> {
            // Deliberately insecure delivery for a loopback-only, opt-in learning profile.
            // Never expose one-time tokens in logs in a real application.
            System.out.println("LOCAL DEMO ONLY - one-time token for "
                    + token.getUsername() + ": " + token.getTokenValue());
            redirect.handle(request, response, token);
        };
    }
}
