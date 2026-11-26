package tr.edu.etu.endofday.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import tr.edu.etu.endofday.support.ApiClient;
import tr.edu.etu.endofday.support.MarketplaceIntegrationTest;

/** POST /businesses and GET /businesses/mine: applications of signed-in users. */
class BusinessApplicationIntegrationTest extends MarketplaceIntegrationTest {

    @Test
    void applicationStartsPendingWithTheApplicantAsOwner() throws Exception {
        Account applicant = signUpCustomer("basvuru");

        long id = json(apply(applicant, "Yılmaz Pastanesi", "FIRIN")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.name").value("Yılmaz Pastanesi"))
                        .andExpect(jsonPath("$.type").value("FIRIN"))
                        .andExpect(jsonPath("$.city").value(city))
                        .andExpect(jsonPath("$.district").value("Çankaya"))
                        .andExpect(jsonPath("$.address").value("Tunalı Hilmi Cad. No: 5"))
                        .andExpect(jsonPath("$.phone").value("0312 555 60 01"))
                        .andExpect(jsonPath("$.approvalStatus").value("BEKLEMEDE"))
                        .andExpect(jsonPath("$.active").value(true)))
                .get("id")
                .asLong();

        assertThat(approvalOf(id)).isEqualTo("BEKLEMEDE");
        assertThat(db().sql("SELECT yetkili_id FROM ISLETME WHERE isletme_id = :id")
                        .param("id", id)
                        .query(Long.class)
                        .single())
                .isEqualTo(applicant.id());
        // The owner role is granted only on approval.
        assertThat(hasRole(applicant.id(), "ISLETME_YETKILISI")).isFalse();
    }

    @Test
    void applicantSeesOnlyTheirOwnApplications() throws Exception {
        Account applicant = signUpCustomer("basvuru.sahip");
        Account other = signUpCustomer("basvuru.diger");
        long first = json(apply(applicant, "Birinci Fırın", "FIRIN")).get("id").asLong();
        long second = json(apply(applicant, "İkinci Kafe", "KAFE")).get("id").asLong();
        apply(other, "Başkasının Lokantası", "RESTORAN").andExpect(status().isCreated());

        applicant
                .client()
                .perform(get("/api/v1/businesses/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[*].id").value(containsInAnyOrder((int) first, (int) second)))
                .andExpect(jsonPath("$.items[*].approvalStatus").value(containsInAnyOrder("BEKLEMEDE", "BEKLEMEDE")));
    }

    @Test
    void invalidApplicationIsRejectedWithFieldErrors() throws Exception {
        Account applicant = signUpCustomer("basvuru.hatali");

        applicant
                .client()
                .postJson("/api/v1/businesses", """
                        {"name": " ", "type": null, "city": "Ankara", "district": "Çankaya",
                         "address": "Adres", "phone": "telefon"}
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EOD-400"))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "phone", "type")));
        applicant.client().postJson("/api/v1/businesses", """
                        {"name": "Fırın", "type": "PASTANE", "city": "Ankara", "district": "Çankaya",
                         "address": "Adres", "phone": "0312 555 60 01"}
                        """).andExpect(status().isBadRequest());
    }

    @Test
    void applicationRequiresSignIn() throws Exception {
        ApiClient anonymous = new ApiClient(mockMvc);
        anonymous.fetchCsrfToken();

        anonymous
                .postJson("/api/v1/businesses", "{}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EOD-401"));
    }

    private ResultActions apply(Account account, String name, String type) throws Exception {
        return account.client().postJson("/api/v1/businesses", """
                {"name": "%s", "type": "%s", "city": "%s", "district": "Çankaya",
                 "address": "Tunalı Hilmi Cad. No: 5", "phone": "0312 555 60 01"}
                """.formatted(name, type, city));
    }
}
