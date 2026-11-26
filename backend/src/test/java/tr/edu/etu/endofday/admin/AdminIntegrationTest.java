package tr.edu.etu.endofday.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tr.edu.etu.endofday.support.MarketplaceIntegrationTest;

/** The administrator panel: applications, account and business activity, roles and listing inspection. */
class AdminIntegrationTest extends MarketplaceIntegrationTest {

    private Account admin;
    private Account applicant;
    private long application;

    @BeforeEach
    void createApplication() throws Exception {
        admin = signUpAdmin("yonetici");
        applicant = signUpCustomer("basvuran", "Ayten", "Koç");
        application = business(applicant.id(), "Yenimahalle", "FIRIN", "BEKLEMEDE", true);
    }

    @Test
    void pendingApplicationsAreListedWithTheirOwner() throws Exception {
        admin.client()
                .perform(get("/api/v1/admin/business-applications?size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == %d)].ownerId".formatted(application))
                        .value(hasItem((int) applicant.id())))
                .andExpect(jsonPath("$.items[?(@.id == %d)].ownerFirstName".formatted(application))
                        .value(hasItem("Ayten")))
                .andExpect(jsonPath("$.items[?(@.id == %d)].approvalStatus".formatted(application))
                        .value(hasItem("BEKLEMEDE")));
    }

    @Test
    void approvalGrantsTheOwnerRoleWhichWorksWithoutSigningInAgain() throws Exception {
        // Before the approval the applicant cannot use the owner endpoints.
        applicant
                .client()
                .perform(get("/api/v1/businesses/" + application + "/listings"))
                .andExpect(status().isForbidden());

        postAs(admin, "/api/v1/admin/businesses/" + application + "/approve")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("ONAYLANDI"));

        assertThat(approvalOf(application)).isEqualTo("ONAYLANDI");
        assertThat(hasRole(applicant.id(), "ISLETME_YETKILISI")).isTrue();
        // The same session, without a new sign-in, now has the role.
        applicant
                .client()
                .perform(get("/api/v1/businesses/" + application + "/listings"))
                .andExpect(status().isOk());
        applicant
                .client()
                .postJson("/api/v1/businesses/" + application + "/listings", """
                        {"title": "Simit Paketi", "description": "Simit ve açma.", "allergenInfo": "Gluten, susam.",
                         "normalPrice": "70.00", "salePrice": "30.00", "totalQuantity": 5,
                         "pickupStart": "2031-03-10T19:00:00", "pickupEnd": "2031-03-10T20:00:00"}
                        """)
                .andExpect(status().isCreated());
    }

    @Test
    void rejectionSetsTheApplicationToRejectedWithoutTheRole() throws Exception {
        postAs(admin, "/api/v1/admin/businesses/" + application + "/reject")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("REDDEDILDI"));

        assertThat(approvalOf(application)).isEqualTo("REDDEDILDI");
        assertThat(hasRole(applicant.id(), "ISLETME_YETKILISI")).isFalse();
        admin.client()
                .perform(get("/api/v1/admin/business-applications?status=REDDEDILDI&size=100"))
                .andExpect(jsonPath("$.items[*].id").value(hasItem((int) application)));
    }

    @Test
    void decidedApplicationCannotBeDecidedAgain() throws Exception {
        postAs(admin, "/api/v1/admin/businesses/" + application + "/approve").andExpect(status().isOk());

        postAs(admin, "/api/v1/admin/businesses/" + application + "/reject")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-026"));
        postAs(admin, "/api/v1/admin/businesses/" + application + "/approve")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-026"));
        postAs(admin, "/api/v1/admin/businesses/999999999/approve").andExpect(status().isNotFound());
    }

    @Test
    void deactivatedUserIsSignedOutOnTheNextRequestAndCanBeActivatedAgain() throws Exception {
        applicant.client().perform(get("/api/v1/me")).andExpect(status().isOk());

        setActive(admin, "/api/v1/admin/users/" + applicant.id() + "/active", false)
                .andExpect(status().isNoContent());

        applicant
                .client()
                .perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("EOD-401"));
        setActive(admin, "/api/v1/admin/users/" + applicant.id() + "/active", true)
                .andExpect(status().isNoContent());
        assertThat(db().sql("SELECT aktif_mi FROM KULLANICI WHERE kullanici_id = :id")
                        .param("id", applicant.id())
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    @Test
    void administratorCannotDeactivateTheirOwnAccount() throws Exception {
        setActive(admin, "/api/v1/admin/users/" + admin.id() + "/active", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-027"));
        setActive(admin, "/api/v1/admin/users/999999999/active", false).andExpect(status().isNotFound());
    }

    @Test
    void deactivatedBusinessDisappearsFromTheSearchUntilActivatedAgain() throws Exception {
        Account owner = signUpOwner("yonetici.isletme");
        long bakery = approvedBusiness(owner.id());
        listing(bakery, "120.00", "60.00", 5);
        Account customer = signUpCustomer("yonetici.musteri");

        setActive(admin, "/api/v1/admin/businesses/" + bakery + "/active", false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        customer.client()
                .perform(get("/api/v1/listings?city=" + city))
                .andExpect(jsonPath("$.totalItems").value(0));

        setActive(admin, "/api/v1/admin/businesses/" + bakery + "/active", true)
                .andExpect(jsonPath("$.active").value(true));
        customer.client()
                .perform(get("/api/v1/listings?city=" + city))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void revokedRoleIsGoneOnTheNextRequestAndCanBeGrantedAgain() throws Exception {
        Account owner = signUpOwner("yonetici.rol");
        long bakery = approvedBusiness(owner.id());
        owner.client()
                .perform(get("/api/v1/businesses/" + bakery + "/listings"))
                .andExpect(status().isOk());

        admin.client()
                .perform(delete("/api/v1/admin/users/" + owner.id() + "/roles/ISLETME_YETKILISI"))
                .andExpect(status().isNoContent());

        assertThat(hasRole(owner.id(), "ISLETME_YETKILISI")).isFalse();
        owner.client()
                .perform(get("/api/v1/businesses/" + bakery + "/listings"))
                .andExpect(status().isForbidden());
        // Revoking a role the user does not hold has no effect.
        admin.client()
                .perform(delete("/api/v1/admin/users/" + owner.id() + "/roles/ISLETME_YETKILISI"))
                .andExpect(status().isNoContent());

        admin.client()
                .perform(put("/api/v1/admin/users/" + owner.id() + "/roles/ISLETME_YETKILISI"))
                .andExpect(status().isNoContent());
        owner.client()
                .perform(get("/api/v1/businesses/" + bakery + "/listings"))
                .andExpect(status().isOk());
    }

    @Test
    void administratorCannotRevokeTheirOwnAdministratorRole() throws Exception {
        admin.client()
                .perform(delete("/api/v1/admin/users/" + admin.id() + "/roles/YONETICI"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD-028"));
        assertThat(hasRole(admin.id(), "YONETICI")).isTrue();
    }

    @Test
    void unknownRoleOrUserIsNotFound() throws Exception {
        admin.client()
                .perform(delete("/api/v1/admin/users/" + applicant.id() + "/roles/PATRON"))
                .andExpect(status().isNotFound());
        admin.client()
                .perform(put("/api/v1/admin/users/999999999/roles/MUSTERI"))
                .andExpect(status().isNotFound());
    }

    @Test
    void usersCanBeSearchedByNameRoleAndActivity() throws Exception {
        admin.client()
                .perform(get("/api/v1/admin/users?q=ayten&role=MUSTERI&active=true&size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == %d)].firstName".formatted(applicant.id()))
                        .value(hasItem("Ayten")))
                .andExpect(jsonPath("$.items[?(@.id == %d)].roles[0]".formatted(applicant.id()))
                        .value(hasItem("MUSTERI")));
        admin.client()
                .perform(get("/api/v1/admin/users?q=" + applicant.email() + "&role=YONETICI"))
                .andExpect(jsonPath("$.totalItems").value(0));
        // % in the search text matches only itself.
        admin.client()
                .perform(get("/api/v1/admin/users?q=%25"))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void administratorInspectsAndDeactivatesAnyListing() throws Exception {
        Account owner = signUpOwner("yonetici.denetim");
        long bakery = approvedBusiness(owner.id());
        long older = listing(bakery, "120.00", "60.00", 5);
        long newer = listing(bakery, "80.00", "30.00", 5, 5, WINDOW_START, WINDOW_END, "PASIF");

        admin.client()
                .perform(get("/api/v1/admin/listings?businessId=" + bakery))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].id").value(contains((int) newer, (int) older)));
        admin.client()
                .perform(get(
                        "/api/v1/admin/listings?businessId=" + bakery + "&status=AKTIF&from=2031-03-10&to=2031-03-10"))
                .andExpect(jsonPath("$.items[*].id").value(contains((int) older)));

        postAs(admin, "/api/v1/admin/listings/" + older + "/deactivate")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASIF"));
        postAs(admin, "/api/v1/admin/listings/999999999/deactivate").andExpect(status().isNotFound());
    }

    private static ResultActions postAs(Account account, String path) throws Exception {
        return account.client().perform(post(path));
    }

    private static ResultActions setActive(Account account, String path, boolean active) throws Exception {
        return account.client()
                .perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{\"active\": " + active + "}"));
    }
}
