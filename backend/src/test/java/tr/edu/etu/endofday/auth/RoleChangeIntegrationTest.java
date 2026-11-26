package tr.edu.etu.endofday.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.edu.etu.endofday.support.ApiClient;
import tr.edu.etu.endofday.support.IntegrationTest;
import tr.edu.etu.endofday.support.TestAccounts;
import tr.edu.etu.endofday.user.Role;
import tr.edu.etu.endofday.user.repository.UserRoleRepository;

/**
 * Role changes take effect on the next request of an open session, because the roles are read from the database on
 * every request. The endpoints that require a role exist only in this test.
 */
@Import(RoleChangeIntegrationTest.RoleControllerConfig.class)
class RoleChangeIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private UserRoleRepository userRoleRepository;

    private ApiClient client;
    private long userId;

    @BeforeEach
    void signInNewCustomer() throws Exception {
        String email = TestAccounts.uniqueEmail("rol");
        client = new ApiClient(mockMvc);
        client.fetchCsrfToken();
        client.register("Ali", "Yılmaz", email, TestAccounts.PASSWORD).andExpect(status().isCreated());
        client.login(email, TestAccounts.PASSWORD).andExpect(status().isOk());
        userId = TestAccounts.idOf(email);
    }

    @AfterEach
    void deleteTestAccounts() {
        TestAccounts.deleteAll();
    }

    @Test
    void revokedRoleNoLongerGrantsAccessOnTheNextRequest() throws Exception {
        client.perform(get("/api/v1/test-roles/customer")).andExpect(status().isOk());

        jdbcClient.sql("""
                        DELETE FROM KULLANICI_ROL
                        WHERE kullanici_id = :userId
                          AND rol_id = (SELECT rol_id FROM ROL WHERE rol_adi = 'MUSTERI')
                        """).param("userId", userId).update();

        client.perform(get("/api/v1/test-roles/customer"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EOD-403"));
        // The user is still signed in, only without the role.
        client.perform(get("/api/v1/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void grantedRoleGrantsAccessOnTheNextRequest() throws Exception {
        client.perform(get("/api/v1/test-roles/business-owner")).andExpect(status().isForbidden());

        userRoleRepository.addRole(userId, Role.ISLETME_YETKILISI);

        client.perform(get("/api/v1/test-roles/business-owner")).andExpect(status().isOk());
        client.perform(get("/api/v1/me"))
                .andExpect(jsonPath("$.roles").value(contains("ISLETME_YETKILISI", "MUSTERI")));
    }

    @Test
    void addingARoleTwiceKeepsOneAssignment() {
        userRoleRepository.addRole(userId, Role.YONETICI);
        userRoleRepository.addRole(userId, Role.YONETICI);

        assertThat(userRoleRepository.findRoles(userId)).containsExactlyInAnyOrder(Role.MUSTERI, Role.YONETICI);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RoleControllerConfig {

        @Bean
        RoleTestController roleTestController() {
            return new RoleTestController();
        }
    }

    @RestController
    @RequestMapping("/api/v1/test-roles")
    static class RoleTestController {

        @GetMapping("/customer")
        @PreAuthorize("hasRole('MUSTERI')")
        String customer() {
            return "ok";
        }

        @GetMapping("/business-owner")
        @PreAuthorize("hasRole('ISLETME_YETKILISI')")
        String businessOwner() {
            return "ok";
        }
    }
}
