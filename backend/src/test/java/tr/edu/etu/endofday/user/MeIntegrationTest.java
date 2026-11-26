package tr.edu.etu.endofday.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tr.edu.etu.endofday.support.ApiClient;
import tr.edu.etu.endofday.support.IntegrationTest;
import tr.edu.etu.endofday.support.TestAccounts;

/** The signed-in user's own account: GET /me, PUT /me and PUT /me/password. */
class MeIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = TestAccounts.PASSWORD;
    private static final String NEW_PASSWORD = "yeniParola456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ApiClient client;
    private String email;

    @BeforeEach
    void signInNewCustomer() throws Exception {
        email = TestAccounts.uniqueEmail("profil");
        client = new ApiClient(mockMvc);
        client.fetchCsrfToken();
        client.register("Ali", "Yılmaz", email, PASSWORD).andExpect(status().isCreated());
        client.login(email, PASSWORD).andExpect(status().isOk());
    }

    @AfterEach
    void deleteTestAccounts() {
        TestAccounts.deleteAll();
    }

    @Test
    void meReturnsTheAccountWithItsRoles() throws Exception {
        client.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TestAccounts.idOf(email)))
                .andExpect(jsonPath("$.firstName").value("Ali"))
                .andExpect(jsonPath("$.lastName").value("Yılmaz"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.roles").value(contains("MUSTERI")));
    }

    @Test
    void meRequiresSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EOD-401"));
    }

    @Test
    void profileUpdateReplacesNameAndPhone() throws Exception {
        updateProfile("""
                        {"firstName": " Ayşe ", "lastName": "Işıklı", "phone": "+90 (532) 410-00-01"}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ayşe"))
                .andExpect(jsonPath("$.lastName").value("Işıklı"))
                .andExpect(jsonPath("$.phone").value("+90 (532) 410-00-01"))
                .andExpect(jsonPath("$.email").value(email));

        client.perform(get("/api/v1/me"))
                .andExpect(jsonPath("$.firstName").value("Ayşe"))
                .andExpect(jsonPath("$.phone").value("+90 (532) 410-00-01"));

        // An empty phone number removes it.
        updateProfile("""
                        {"firstName": "Ayşe", "lastName": "Işıklı", "phone": "  "}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist());
        assertThat(jdbcClient
                        .sql("SELECT telefon FROM KULLANICI WHERE eposta = :email")
                        .param("email", email)
                        .query()
                        .singleRow()
                        .get("telefon"))
                .isNull();
    }

    @Test
    void profileUpdateIgnoresAnEmailAddressInTheBody() throws Exception {
        updateProfile("""
                        {"firstName": "Ali", "lastName": "Yılmaz", "email": "baska@hesap.test"}
                        """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void invalidProfileUpdateIsRejectedWithFieldErrors() throws Exception {
        updateProfile("""
                        {"firstName": "", "lastName": "%s", "phone": "telefon"}
                        """.formatted("y".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-400"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'firstName')].message").value(contains("Ad zorunludur.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'lastName')].message")
                        .value(contains("Soyad en fazla 50 karakter olabilir.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'phone')].message")
                        .value(contains("Geçerli bir telefon numarası giriniz.")));
    }

    @Test
    void profileUpdateWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/me")
                        .session(client.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"Ali\", \"lastName\": \"Yılmaz\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EOD-403"));
    }

    @Test
    void passwordChangeReplacesThePassword() throws Exception {
        changePassword(PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        String hash = jdbcClient
                .sql("SELECT parola_ozeti FROM KULLANICI WHERE eposta = :email")
                .param("email", email)
                .query(String.class)
                .single();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, hash)).isTrue();

        // The session stays signed in.
        client.perform(get("/api/v1/me")).andExpect(status().isOk());

        ApiClient other = new ApiClient(mockMvc);
        other.fetchCsrfToken();
        other.login(email, PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EOD-010"));
        other.login(email, NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void passwordChangeWithWrongCurrentPasswordIsRejected() throws Exception {
        changePassword("yanlisParola1", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-012"))
                .andExpect(jsonPath("$.detail").value("Mevcut parola hatalı."));

        ApiClient other = new ApiClient(mockMvc);
        other.fetchCsrfToken();
        other.login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void newPasswordMustFollowThePasswordPolicy() throws Exception {
        changePassword(PASSWORD, "sadeceharf")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-400"))
                .andExpect(jsonPath("$.errors[?(@.field == 'newPassword')].message")
                        .value(contains("Parola en az bir harf ve bir rakam içermelidir.")));
    }

    @Test
    void passwordChangeRequiresTheCurrentPassword() throws Exception {
        client.perform(put("/api/v1/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"%s\"}".formatted(NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'currentPassword')].message")
                        .value(contains("Parola zorunludur.")));
    }

    @Test
    void noAccountResponseContainsThePasswordHash() throws Exception {
        List<String> bodies = List.of(
                body(client.perform(get("/api/v1/me"))),
                body(updateProfile("{\"firstName\": \"Ali\", \"lastName\": \"Yılmaz\"}")));

        assertThat(bodies)
                .allSatisfy(body -> assertThat(body)
                        .doesNotContain("$2a$")
                        .doesNotContainIgnoringCase("password")
                        .doesNotContainIgnoringCase("parola"));
    }

    private ResultActions updateProfile(String json) throws Exception {
        return client.perform(
                put("/api/v1/me").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions changePassword(String currentPassword, String newPassword) throws Exception {
        return client.perform(put("/api/v1/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword": "%s", "newPassword": "%s"}
                        """.formatted(currentPassword, newPassword)));
    }

    private static String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
