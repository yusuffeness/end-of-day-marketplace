package tr.edu.etu.endofday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tr.edu.etu.endofday.support.IntegrationTest;

class SecurityIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootPathIsPublicAndServesTheWelcomePage() throws Exception {
        // The welcome page is served by forwarding to index.html; MockMvc records the forward
        // instead of executing it, so the page itself is checked separately below.
        mockMvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
    }

    @Test
    void indexPageIsPublicAndServedAsHtml() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                // The page declares its encoding with <meta charset="utf-8">, so decode it as UTF-8.
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("<meta charset=\"utf-8\">", "Yakında hizmetinizde."));
    }

    @Test
    void stylesheetIsPublic() throws Exception {
        mockMvc.perform(get("/css/main.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
    }

    @Test
    void scriptIsPublic() throws Exception {
        mockMvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"));
    }

    @Test
    void healthEndpointIsPublicAndReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void apiRequestWithoutSignInIsRejectedWithUnauthorizedProblem() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EOD-401"))
                .andExpect(jsonPath("$.detail").value("Bu işlem için giriş yapmalısınız."));
    }

    @Test
    void postWithoutCsrfTokenIsRejectedBeforeAuthorization() throws Exception {
        mockMvc.perform(post("/api/v1/ping"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EOD-403"))
                .andExpect(jsonPath("$.detail").value("Bu işlem için yetkiniz yok."));
    }

    @Test
    @WithMockUser
    void signedInRequestToUnknownPathReturnsNotFoundProblem() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EOD-404"));
    }
}
