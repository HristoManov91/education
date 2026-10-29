package bg.hristomanov.education.boot4;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contract regression: поддържаме едновременно стария и новия клиент. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiVersioningHttpTest {

    @Autowired
    MockMvc mvc;

    @Test
    void oldClientKeepsOriginalPayload() throws Exception {
        mvc.perform(get("/dogs").header("X-Dogs-Version", "1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dogs[0].name").value("Rex"))
                .andExpect(jsonPath("$.dogs[0].description").doesNotExist());
    }

    @Test
    void newClientReceivesExtendedPayload() throws Exception {
        mvc.perform(get("/dogs").header("X-Dogs-Version", "2.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dogs[0].description").value("Rescued dog"));
    }

    @Test
    void unsupportedVersionIsRejectedInsteadOfSilentlyDowngrading() throws Exception {
        mvc.perform(get("/dogs").header("X-Dogs-Version", "99.0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingVersionIsNotSilentlyReinterpreted() throws Exception {
        mvc.perform(get("/dogs")).andExpect(status().isBadRequest());
    }
}
