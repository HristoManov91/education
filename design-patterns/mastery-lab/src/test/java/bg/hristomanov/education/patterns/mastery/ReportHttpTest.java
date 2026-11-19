package bg.hristomanov.education.patterns.mastery;

import bg.hristomanov.education.patterns.mastery.archive.LegacyArchiveClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = DesignPatternsApplication.class)
@AutoConfigureMockMvc
class ReportHttpTest {
    @Autowired MockMvc mvc;
    @Autowired LegacyArchiveClient archive;

    @Test void validCsvRequestTraversesTheRealSpringCompositionRoot() throws Exception {
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reference":"HTTP-1","owner":"Ada","items":["Hello","World"],"format":"CSV"}
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.archiveId").value("LEGACY:HTTP-1"))
                .andExpect(jsonPath("$.format").value("CSV"));
        assertThat(archive.readLegacyText("HTTP-1")).contains("\"HTTP-1\",\"Hello\"");
    }

    @Test void invalidInputReturns400WithoutArchiving() throws Exception {
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reference":"HTTP-2","owner":"","items":["Hello"],"format":"TEXT"}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        assertThat(archive.readLegacyText("HTTP-2")).isNull();
    }

    @Test void unsupportedFormatReturns400WithoutArchiving() throws Exception {
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reference":"HTTP-3","owner":"Ada","items":["Hello"],"format":"PDF"}
                    """))
                .andExpect(status().isBadRequest());
        assertThat(archive.readLegacyText("HTTP-3")).isNull();
    }

    @Test void duplicateArchiveFailureDoesNotClaimCreated() throws Exception {
        String body = """
                {"reference":"HTTP-4","owner":"Ada","items":["Hello"],"format":"TEXT"}
                """;
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().is5xxServerError());
    }
}
