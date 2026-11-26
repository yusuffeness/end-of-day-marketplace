package tr.edu.etu.endofday.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tr.edu.etu.endofday.support.ApiClient;
import tr.edu.etu.endofday.support.IntegrationTest;
import tr.edu.etu.endofday.support.TestAccounts;

/** Registration, sign-in, sign-out and the session, through the real endpoints and the real CSRF protection. */
class AuthIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = TestAccounts.PASSWORD;
    private static final String BAD_CREDENTIALS_DETAIL = "E-posta veya parola hatalı.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private ApiClient client;

    @BeforeEach
    void newClient() {
        client = new ApiClient(mockMvc);
    }

    @AfterEach
    void deleteTestAccounts() {
        TestAccounts.deleteAll();
    }

    @Test
    void fullClientFlowWithRealCsrfToken() throws Exception {
        String email = TestAccounts.uniqueEmail("akis");

        client.fetchCsrfToken().andExpect(cookie().exists(ApiClient.CSRF_COOKIE));
        String tokenBeforeLogin = client.csrfToken();
        assertThat(tokenBeforeLogin).isNotBlank();

        client.register("Ayşe", "Işık", email, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles").value(contains("MUSTERI")));

        client.login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TestAccounts.idOf(email)))
                .andExpect(jsonPath("$.firstName").value("Ayşe"))
                .andExpect(jsonPath("$.lastName").value("Işık"))
                .andExpect(jsonPath("$.roles").value(contains("MUSTERI")));
        assertThat(client.session()).isNotNull();
        assertThat(client.csrfToken()).isNotBlank().isNotEqualTo(tokenBeforeLogin);

        client.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.roles").value(contains("MUSTERI")));

        MockHttpSession signedInSession = client.session();
        client.logout().andExpect(status().isNoContent());
        assertThat(signedInSession.isInvalid()).isTrue();
        assertThat(client.session()).isNull();
        assertThat(client.csrfToken()).isNull();

        client.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EOD-401"));
    }

    @Test
    void csrfEndpointIsPublicAndCreatesNoSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(ApiClient.CSRF_COOKIE))
                .andExpect(cookie().httpOnly(ApiClient.CSRF_COOKIE, false))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void registerWithoutCsrfTokenIsForbidden() throws Exception {
        String email = TestAccounts.uniqueEmail("csrfsiz");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Ali", "Yılmaz", email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EOD-403"));

        assertThat(countUsers(email)).isZero();
    }

    @Test
    void loginWithoutCsrfTokenIsForbidden() throws Exception {
        String email = registerCustomer("csrfsiz");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EOD-403"))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void loginWithCsrfTokenThatDoesNotMatchTheCookieIsForbidden() throws Exception {
        String email = registerCustomer("sahte");

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie(ApiClient.CSRF_COOKIE, "cookie-value"))
                        .header(ApiClient.CSRF_HEADER, "other-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EOD-403"));
    }

    @Test
    void loginChangesTheSessionIdAndKeepsTheSession() throws Exception {
        String email = registerCustomer("oturum");
        MockHttpSession anonymousSession = new MockHttpSession();
        String idBeforeLogin = anonymousSession.getId();
        client.useSession(anonymousSession);

        client.login(email, PASSWORD).andExpect(status().isOk());

        assertThat(client.session()).isNotNull();
        assertThat(client.session().getId()).isNotEqualTo(idBeforeLogin);
        client.perform(get("/api/v1/me")).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        String email = registerCustomer("yanlis");

        String wrongPassword = client.login(email, "baskaParola1")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EOD-010"))
                .andExpect(jsonPath("$.detail").value(BAD_CREDENTIALS_DETAIL))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String unknownEmail = client.login(TestAccounts.uniqueEmail("yok"), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EOD-010"))
                .andExpect(jsonPath("$.detail").value(BAD_CREDENTIALS_DETAIL))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(unknownEmail).isEqualTo(wrongPassword);
        assertThat(client.session()).isNull();
    }

    @Test
    void inactiveUserCannotSignIn() throws Exception {
        String email = registerCustomer("pasif");
        setActive(email, false);

        client.login(email, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EOD-011"))
                .andExpect(jsonPath("$.detail").value("Hesabınız pasif durumda."));
        assertThat(client.session()).isNull();
    }

    @Test
    void inactiveUserWithWrongPasswordGetsTheBadCredentialsAnswer() throws Exception {
        String email = registerCustomer("pasif");
        setActive(email, false);

        client.login(email, "baskaParola1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EOD-010"));
    }

    @Test
    void userDeactivatedDuringTheSessionIsRejectedOnTheNextRequest() throws Exception {
        String email = registerCustomer("kapatilan");
        client.login(email, PASSWORD).andExpect(status().isOk());
        MockHttpSession signedInSession = client.session();
        client.perform(get("/api/v1/me")).andExpect(status().isOk());

        setActive(email, false);

        client.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EOD-401"))
                .andExpect(jsonPath("$.detail").value("Bu işlem için giriş yapmalısınız."));
        assertThat(signedInSession.isInvalid()).isTrue();

        // The session stays invalid even when the account is activated again.
        setActive(email, true);
        client.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void registrationDoesNotSignTheUserIn() throws Exception {
        client.fetchCsrfToken();
        client.register("Ali", "Yılmaz", TestAccounts.uniqueEmail("kayit"), PASSWORD)
                .andExpect(status().isCreated());

        assertThat(client.session()).isNull();
        client.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void registrationTrimsTheFieldsAndAcceptsAPhoneNumber() throws Exception {
        String email = TestAccounts.uniqueEmail("telefon");
        client.fetchCsrfToken();

        client.postJson("/api/v1/auth/register", """
                        {"firstName": "  Ali ", "lastName": " Yılmaz", "email": "  %s ",
                         "phone": " 0532 410 00 01 ", "password": "%s"}
                        """.formatted(email, PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Ali"))
                .andExpect(jsonPath("$.lastName").value("Yılmaz"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phone").value("0532 410 00 01"));
    }

    @Test
    void registrationStoresAMatchingBcryptHashAndOnlyTheCustomerRole() throws Exception {
        String email = TestAccounts.uniqueEmail("ozet");
        LocalDateTime before = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);

        client.fetchCsrfToken();
        client.register("Ali", "Yılmaz", email, PASSWORD).andExpect(status().isCreated());

        Map<String, Object> row = jdbcClient
                .sql("SELECT parola_ozeti, aktif_mi, kayit_zamani FROM KULLANICI WHERE eposta = :email")
                .param("email", email)
                .query()
                .singleRow();
        String hash = (String) row.get("parola_ozeti");
        assertThat(hash).startsWith("$2a$12$").hasSize(60).doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
        assertThat(row.get("aktif_mi")).isEqualTo(true);
        assertThat((LocalDateTime) row.get("kayit_zamani"))
                .isBetween(before, LocalDateTime.now(clock).plusSeconds(1));

        List<String> roles =
                jdbcClient.sql("""
                        SELECT r.rol_adi
                        FROM KULLANICI_ROL kr
                        JOIN ROL r ON r.rol_id = kr.rol_id
                        JOIN KULLANICI k ON k.kullanici_id = kr.kullanici_id
                        WHERE k.eposta = :email
                        """).param("email", email).query(String.class).list();
        assertThat(roles).containsExactly("MUSTERI");
    }

    @Test
    void emailAddressIsStoredInLowerCaseAndUniqueRegardlessOfCase() throws Exception {
        String mixedCase = "Ali." + UUID.randomUUID().toString().substring(0, 8) + "@Hesap.Test";
        String lowerCase = mixedCase.toLowerCase(Locale.ROOT);
        client.fetchCsrfToken();

        client.register("Ali", "Yılmaz", mixedCase, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(lowerCase));
        assertThat(countUsers(lowerCase)).isOne();

        client.register("Ali", "Yılmaz", mixedCase.toUpperCase(Locale.ROOT), PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-009"))
                .andExpect(jsonPath("$.detail").value("Bu e-posta adresiyle kayıtlı bir hesap var."));
        assertThat(countUsers(lowerCase)).isOne();

        client.login(lowerCase, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(lowerCase));
    }

    @Test
    void signInAcceptsTheEmailAddressInAnyCase() throws Exception {
        String email = registerCustomer("buyuk");

        client.login("  " + email.toUpperCase(Locale.ROOT) + " ", PASSWORD).andExpect(status().isOk());
    }

    static Stream<Arguments> invalidRegistrations() {
        String valid = "parola123";
        return Stream.of(
                Arguments.of("not-an-email", valid, "email", "Geçerli bir e-posta adresi giriniz."),
                Arguments.of("ali@ornek", valid, "email", "Geçerli bir e-posta adresi giriniz."),
                Arguments.of("", valid, "email", "E-posta adresi zorunludur."),
                Arguments.of(null, "parola1", "password", "Parola 8 ile 72 karakter arasında olmalıdır."),
                Arguments.of(null, "parolaparola", "password", "Parola en az bir harf ve bir rakam içermelidir."),
                Arguments.of(null, "1234567890", "password", "Parola en az bir harf ve bir rakam içermelidir."),
                Arguments.of(null, "a1" + "x".repeat(71), "password", "Parola 8 ile 72 karakter arasında olmalıdır."),
                Arguments.of(
                        null,
                        "a1" + "ş".repeat(40),
                        "password",
                        "Parola en fazla 72 bayt olabilir; Türkçe karakterler 2 bayt sayılır."),
                Arguments.of(null, "", "password", "Parola 8 ile 72 karakter arasında olmalıdır."));
    }

    @ParameterizedTest(name = "[{index}] {2}: {3}")
    @MethodSource("invalidRegistrations")
    void invalidRegistrationIsRejectedWithFieldErrors(String email, String password, String field, String message)
            throws Exception {
        String address = email == null ? TestAccounts.uniqueEmail("gecersiz") : email;
        client.fetchCsrfToken();

        client.register("Ali", "Yılmaz", address, password)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EOD-400"))
                .andExpect(jsonPath("$.detail").value("Gönderilen bilgiler geçersiz."))
                .andExpect(
                        jsonPath("$.errors[?(@.field == '%s')].message", field).value(hasItem(message)));

        assertThat(countUsers(address)).isZero();
    }

    @Test
    void passwordOfExactly72AsciiCharactersIsAccepted() throws Exception {
        String email = TestAccounts.uniqueEmail("uzun");
        String password = "a1" + "x".repeat(70);
        client.fetchCsrfToken();

        client.register("Ali", "Yılmaz", email, password).andExpect(status().isCreated());
        client.login(email, password).andExpect(status().isOk());
    }

    @Test
    void everyBrokenRuleIsListedForItsField() throws Exception {
        client.fetchCsrfToken();

        client.postJson("/api/v1/auth/register", """
                        {"firstName": " ", "lastName": "%s", "email": "x", "phone": "abc", "password": "kisa"}
                        """.formatted("y".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(6)))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder("email", "firstName", "lastName", "password", "password", "phone")));
    }

    @Test
    void logoutWithoutSessionAnswersNoContent() throws Exception {
        client.fetchCsrfToken();

        client.logout().andExpect(status().isNoContent());
    }

    @Test
    void logoutWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isForbidden());
    }

    @Test
    void noResponseContainsThePasswordHash() throws Exception {
        String email = TestAccounts.uniqueEmail("gizli");
        client.fetchCsrfToken();

        List<String> bodies = List.of(
                body(client.register("Ali", "Yılmaz", email, PASSWORD).andExpect(status().isCreated())),
                body(client.login(email, PASSWORD).andExpect(status().isOk())),
                body(client.perform(get("/api/v1/me")).andExpect(status().isOk())));

        String hash = jdbcClient
                .sql("SELECT parola_ozeti FROM KULLANICI WHERE eposta = :email")
                .param("email", email)
                .query(String.class)
                .single();
        assertThat(bodies)
                .allSatisfy(body -> assertThat(body)
                        .doesNotContain(hash, "$2a$", PASSWORD)
                        .doesNotContainIgnoringCase("password")
                        .doesNotContainIgnoringCase("parola"));
    }

    private String registerCustomer(String localPart) throws Exception {
        String email = TestAccounts.uniqueEmail(localPart);
        ApiClient registrar = new ApiClient(mockMvc);
        registrar.fetchCsrfToken();
        registrar.register("Ali", "Yılmaz", email, PASSWORD).andExpect(status().isCreated());
        client.fetchCsrfToken();
        return email;
    }

    private void setActive(String email, boolean active) {
        jdbcClient
                .sql("UPDATE KULLANICI SET aktif_mi = :active WHERE eposta = :email")
                .param("active", active)
                .param("email", email)
                .update();
    }

    private long countUsers(String email) {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM KULLANICI WHERE eposta = :email")
                .param("email", email)
                .query(Long.class)
                .single();
    }

    private static String body(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String registerJson(String firstName, String lastName, String email, String password) {
        return """
                {"firstName": "%s", "lastName": "%s", "email": "%s", "password": "%s"}
                """.formatted(firstName, lastName, email, password);
    }

    private static String loginJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
