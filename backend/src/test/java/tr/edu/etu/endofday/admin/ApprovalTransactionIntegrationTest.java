package tr.edu.etu.endofday.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tr.edu.etu.endofday.support.MarketplaceIntegrationTest;
import tr.edu.etu.endofday.user.Role;
import tr.edu.etu.endofday.user.repository.UserRoleRepository;

/** The approval and the role assignment are one transaction: when the role cannot be granted, nothing changes. */
class ApprovalTransactionIntegrationTest extends MarketplaceIntegrationTest {

    @MockitoSpyBean
    private UserRoleRepository userRoleRepository;

    @Test
    void failedRoleAssignmentRollsBackTheApproval() throws Exception {
        Account admin = signUpAdmin("onay.yonetici");
        Account applicant = signUpCustomer("onay.basvuran");
        long application = business(applicant.id(), "Çankaya", "KAFE", "BEKLEMEDE", true);
        doThrow(new DataAccessResourceFailureException("connection lost"))
                .when(userRoleRepository)
                .addRole(anyLong(), eq(Role.ISLETME_YETKILISI));

        admin.client()
                .perform(post("/api/v1/admin/businesses/" + application + "/approve"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("EOD-500"));

        assertThat(approvalOf(application)).isEqualTo("BEKLEMEDE");
        assertThat(hasRole(applicant.id(), "ISLETME_YETKILISI")).isFalse();
    }
}
