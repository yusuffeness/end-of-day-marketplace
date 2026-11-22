package tr.edu.etu.endofday;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
