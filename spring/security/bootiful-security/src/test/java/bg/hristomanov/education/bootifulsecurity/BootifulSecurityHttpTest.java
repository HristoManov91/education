package bg.hristomanov.education.bootifulsecurity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-demo")
class BootifulSecurityHttpTest {

    @Autowired
    MockMvc mvc;

    @Test
    void publicEndpointIsAccessibleToAnonymousUsers() throws Exception {
        mvc.perform(get("/api/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("public"));
    }

    @Test
    void protectedEndpointRedirectsAnonymousUsersToLogin() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void authenticatedUserCanReadTheirProfile() throws Exception {
        mvc.perform(get("/api/me").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void adminRoleAloneIsInsufficientForSensitiveEndpoint() throws Exception {
        int responseStatus = mvc.perform(get("/api/admin/report")
                        .with(user("admin").roles("ADMIN")))
                .andReturn().getResponse().getStatus();

        // The framework may use a factor-specific redirect or denial,
        // but ROLE_ADMIN without both factor proofs must never be enough.
        assertThat(responseStatus).isBetween(300, 499);
    }

    @Test
    void bothFactorsAndAdminRoleAllowSensitiveRead() throws Exception {
        mvc.perform(get("/api/admin/report").with(mfaAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedBy").value("admin"));
    }

    @Test
    void secondFactorDoesNotReplaceBusinessRoleAuthorization() throws Exception {
        mvc.perform(get("/api/admin/report").with(user("alice").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY),
                        FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.OTT_AUTHORITY))))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfIsRequiredEvenAfterSuccessfulMfa() throws Exception {
        mvc.perform(post("/api/admin/operation").with(mfaAdmin()))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/operation")
                        .with(mfaAdmin())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("executed"));
    }

    @Test
    void unknownEndpointDoesNotBecomePublicByAccident() throws Exception {
        mvc.perform(get("/api/forgot-to-protect").with(mfaAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void realPasswordLoginAcceptsHashedDemoCredential() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("username", "alice")
                        .param("password", "local-demo-password"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void realPasswordLoginRejectsWrongPassword() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("username", "alice")
                        .param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    private static SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor mfaAdmin() {
        // Test-only simulated authentication: proves HTTP authorization, not the
        // independent ownership of the one-time-token delivery channel.
        return user("admin").authorities(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY),
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.OTT_AUTHORITY));
    }
}
