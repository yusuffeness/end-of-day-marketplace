package tr.edu.etu.endofday.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.edu.etu.endofday.support.IntegrationTest;

@WithMockUser
@Import(ErrorHandlingIntegrationTest.ErrorControllerConfig.class)
class ErrorHandlingIntegrationTest extends IntegrationTest {

    private static final String UNEXPECTED_MESSAGE = "internal failure that must stay on the server";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiExceptionIsReturnedWithItsStatusCodeAndDetail() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("EOD-001"))
                .andExpect(jsonPath("$.detail").value("Test çakışması"));
    }

    @Test
    void unexpectedExceptionIsReturnedAsGenericProblemWithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EOD-500"))
                .andExpect(jsonPath("$.detail").value("Beklenmeyen bir hata oluştu."))
                .andExpect(content().string(not(containsString(UNEXPECTED_MESSAGE))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at tr.edu.etu"))))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void invalidRequestBodyIsReturnedAsBadRequestProblem() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/validated")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"quantity\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EOD-400"))
                .andExpect(jsonPath("$.detail").value("Gönderilen bilgiler geçersiz."));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ErrorControllerConfig {

        @Bean
        ErrorTestController errorTestController() {
            return new ErrorTestController();
        }
    }

    @RestController
    @RequestMapping("/api/v1/test-errors")
    static class ErrorTestController {

        @GetMapping("/conflict")
        void conflict() {
            throw new ApiException(HttpStatus.CONFLICT, "EOD-001", "Test çakışması");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException(UNEXPECTED_MESSAGE);
        }

        @PostMapping("/validated")
        TestRequest validated(@Valid @RequestBody TestRequest request) {
            return request;
        }
    }

    record TestRequest(@NotBlank String name, @Positive int quantity) {}
}
