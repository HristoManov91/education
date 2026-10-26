package bg.hristomanov.education.aitesting.api;

import bg.hristomanov.education.aitesting.domain.RegisteredCustomer;
import bg.hristomanov.education.aitesting.domain.RegistrationCommand;
import bg.hristomanov.education.aitesting.security.SecurityConfig;
import bg.hristomanov.education.aitesting.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
@Import(SecurityConfig.class)
class CustomerControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CustomerService service;

    private static final String VALID_JSON =
            """
            {"email":"alice@example.com","birthDate":"1990-01-01","acceptedTerms":true}
            """;

    @Test
    @WithMockUser(roles = "STAFF")
    void validRequestUsesMvcBindingValidationAndJsonSerialization() throws Exception {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(service.register(any(RegistrationCommand.class))).thenReturn(
                new RegisteredCustomer(id, "alice@example.com",
                        Instant.parse("2026-10-26T08:00:00Z")));

        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/customers/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.registeredAt").exists());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void invalidEmailIsRejectedByRealBeanValidationNotByAMockedController() throws Exception {
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid\",\"birthDate\":\"1990-01-01\",\"acceptedTerms\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousRequestIsRejectedBeforeReachingBusinessService() throws Exception {
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void authenticatedUserWithoutRoleCannotRegisterCustomers() throws Exception {
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
                .andExpect(status().isForbidden());
    }
}
