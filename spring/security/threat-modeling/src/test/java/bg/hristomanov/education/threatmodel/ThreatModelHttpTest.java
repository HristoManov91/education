package bg.hristomanov.education.threatmodel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security invariant тестове за Spoofing, Information Disclosure, Elevation of
 * Privilege и CSRF; не са автоматичен penetration test за всички STRIDE категории.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ThreatModelHttpTest {

    @Autowired
    MockMvc mvc;

    @Test
    void anonymousCannotReadRecords() throws Exception {
        mvc.perform(get("/api/records/alice"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void spoofedIdentityHeaderNeverOverridesAuthenticatedPrincipal() throws Exception {
        mvc.perform(get("/api/records/alice")
                        .header("X-User-Id", "alice")
                        .with(user("bob").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanReadButAnotherAuthenticatedUserCannot() throws Exception {
        mvc.perform(get("/api/records/alice").with(user("alice").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("alice"));

        mvc.perform(get("/api/records/alice").with(user("bob").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownRoutesAreNotPublicByAccident() throws Exception {
        mvc.perform(get("/api/debug").with(user("alice").roles("REVIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewerAndOwnershipAreBothRequired() throws Exception {
        mvc.perform(post("/api/records/alice/approve")
                        .with(user("alice").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/records/alice/approve")
                        .with(user("bob").roles("REVIEWER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/records/alice/approve")
                        .with(user("alice").roles("REVIEWER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("approved"));
    }

    @Test
    void csrfIsNotDisabledForBrowserCapableHttpBasic() throws Exception {
        mvc.perform(post("/api/records/alice/approve")
                        .with(user("alice").roles("REVIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userControlledHeadersDoNotGrantReviewerAuthority() throws Exception {
        mvc.perform(post("/api/records/alice/approve")
                        .header("X-Roles", "ROLE_REVIEWER")
                        .with(user("alice").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
