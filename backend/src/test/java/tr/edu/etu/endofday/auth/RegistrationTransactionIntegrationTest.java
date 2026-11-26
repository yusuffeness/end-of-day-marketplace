package tr.edu.etu.endofday.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tr.edu.etu.endofday.support.ApiClient;
import tr.edu.etu.endofday.support.IntegrationTest;
import tr.edu.etu.endofday.support.OwnerJdbc;
import tr.edu.etu.endofday.support.TestAccounts;
import tr.edu.etu.endofday.user.Role;
import tr.edu.etu.endofday.user.repository.UserRoleRepository;

/**
 * The account and its MUSTERI role are written in one transaction: when the role cannot be assigned, the KULLANICI row
 * is rolled back as well, so no account without a role is left behind.
 */
class RegistrationTransactionIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private UserRoleRepository userRoleRepository;

    private ApiClient client;

    @BeforeEach
    void newClient() throws Exception {
        client = new ApiClient(mockMvc);
        client.fetchCsrfToken();
    }

    @AfterEach
    void deleteTestAccounts() {
        TestAccounts.deleteAll();
    }

    @Test
    void failedRoleInsertRollsBackTheAccount() throws Exception {
        String email = TestAccounts.uniqueEmail("gerial");
        doThrow(new DataAccessResourceFailureException("connection lost"))
                .when(userRoleRepository)
                .addRole(anyLong(), eq(Role.MUSTERI));

        client.register("Ali", "Yılmaz", email, TestAccounts.PASSWORD)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("EOD-500"));

        assertThat(countUsers(email)).isZero();
    }

    @Test
    void missingRoleRollsBackTheAccount() throws Exception {
        String email = TestAccounts.uniqueEmail("rolsuz");
        doReturn(false).when(userRoleRepository).addRole(anyLong(), eq(Role.MUSTERI));

        client.register("Ali", "Yılmaz", email, TestAccounts.PASSWORD)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("EOD-500"));

        assertThat(countUsers(email)).isZero();
    }

    @Test
    void accountCanBeRegisteredAfterARolledBackAttempt() throws Exception {
        String email = TestAccounts.uniqueEmail("tekrar");
        doReturn(false).when(userRoleRepository).addRole(anyLong(), eq(Role.MUSTERI));
        client.register("Ali", "Yılmaz", email, TestAccounts.PASSWORD).andExpect(status().isInternalServerError());

        doCallRealMethod().when(userRoleRepository).addRole(anyLong(), eq(Role.MUSTERI));
        client.register("Ali", "Yılmaz", email, TestAccounts.PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles").value(contains("MUSTERI")));

        assertThat(countUsers(email)).isOne();
        assertThat(userRoleRepository.findRoles(TestAccounts.idOf(email))).containsExactly(Role.MUSTERI);
    }

    /** Reads through the owner connection, outside the application's transactions. */
    private static long countUsers(String email) {
        return OwnerJdbc.client()
                .sql("SELECT COUNT(*) FROM KULLANICI WHERE eposta = :email")
                .param("email", email)
                .query(Long.class)
                .single();
    }
}
