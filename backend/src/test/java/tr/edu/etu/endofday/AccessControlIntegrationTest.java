package tr.edu.etu.endofday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tr.edu.etu.endofday.support.ApiClient;
import tr.edu.etu.endofday.support.MarketplaceIntegrationTest;

/**
 * Report section 7.3: every protected endpoint rejects anonymous requests, users without the required role, and users
 * who try to reach another user's or another business's resources. Each case lists the endpoint, the caller and the
 * expected status and code.
 */
class AccessControlIntegrationTest extends MarketplaceIntegrationTest {

    private Account customer;
    private Account otherCustomer;
    private Account owner;
    private Account otherOwner;
    private long bakery;
    private long listing;
    private long orderId;

    @BeforeEach
    void createResources() throws Exception {
        customer = signUpCustomer("erisim.musteri");
        otherCustomer = signUpCustomer("erisim.diger");
        owner = signUpOwner("erisim.sahip");
        otherOwner = signUpOwner("erisim.baska");
        bakery = approvedBusiness(owner.id());
        approvedBusiness(otherOwner.id());
        listing = listing(bakery, "120.00", "60.00", 5);
        orderId = placedOrder(customer, bakery, listing, 1);
    }

    @Test
    void anonymousRequestsToProtectedEndpointsAreUnauthorized() throws Exception {
        ApiClient anonymous = new ApiClient(mockMvc);
        anonymous.fetchCsrfToken();
        List<String> failures = new ArrayList<>();
        for (Endpoint endpoint : allEndpoints()) {
            expect(failures, anonymous, endpoint, 401, "EOD-401");
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void customerCannotUseOwnerOrAdministratorEndpoints() throws Exception {
        List<String> failures = new ArrayList<>();
        for (Endpoint endpoint : ownerEndpoints()) {
            expect(failures, customer.client(), endpoint, 403, "EOD-403");
        }
        for (Endpoint endpoint : adminEndpoints()) {
            expect(failures, customer.client(), endpoint, 403, "EOD-403");
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void ownerCannotReachAnotherBusiness() throws Exception {
        List<String> failures = new ArrayList<>();
        for (Endpoint endpoint : ownerEndpoints()) {
            String code = endpoint.path().endsWith("/pickups/verify") ? "EOD-008" : "EOD-403";
            expect(failures, otherOwner.client(), endpoint, 403, code);
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void ownerCannotUseAdministratorEndpoints() throws Exception {
        List<String> failures = new ArrayList<>();
        for (Endpoint endpoint : adminEndpoints()) {
            expect(failures, owner.client(), endpoint, 403, "EOD-403");
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void customerCannotReachAnotherCustomersOrder() throws Exception {
        List<String> failures = new ArrayList<>();
        expect(
                failures,
                otherCustomer.client(),
                new Endpoint(HttpMethod.GET, "/api/v1/orders/" + orderId, null),
                404,
                "EOD-404");
        expect(
                failures,
                otherCustomer.client(),
                new Endpoint(
                        HttpMethod.POST, "/api/v1/orders/" + orderId + "/cancel", "{\"requestId\": \"erisim-iptal\"}"),
                404,
                "EOD-404");
        expect(
                failures,
                otherCustomer.client(),
                new Endpoint(HttpMethod.POST, "/api/v1/orders/" + orderId + "/review", "{\"rating\": 1}"),
                404,
                "EOD-404");
        assertThat(failures).isEmpty();
        assertThat(orderStatusOf(orderId)).isEqualTo("ONAYLANDI");
    }

    private List<Endpoint> allEndpoints() {
        List<Endpoint> endpoints = new ArrayList<>(List.of(
                new Endpoint(HttpMethod.GET, "/api/v1/me", null),
                new Endpoint(HttpMethod.GET, "/api/v1/listings", null),
                new Endpoint(HttpMethod.GET, "/api/v1/listings/" + listing, null),
                new Endpoint(HttpMethod.GET, "/api/v1/orders", null),
                new Endpoint(HttpMethod.GET, "/api/v1/orders/" + orderId, null),
                new Endpoint(HttpMethod.POST, "/api/v1/orders", "{}"),
                new Endpoint(HttpMethod.POST, "/api/v1/businesses", "{}"),
                new Endpoint(HttpMethod.GET, "/api/v1/businesses/mine", null)));
        endpoints.addAll(ownerEndpoints());
        endpoints.addAll(adminEndpoints());
        return endpoints;
    }

    /** Endpoints of the business {@link #bakery}, owned by {@link #owner}. */
    private List<Endpoint> ownerEndpoints() {
        String business = "/api/v1/businesses/" + bakery;
        return List.of(
                new Endpoint(HttpMethod.PUT, business, """
                        {"name": "Ad", "city": "Ankara", "district": "Çankaya", "address": "Adres",
                         "phone": "0312 555 00 00"}
                        """),
                new Endpoint(HttpMethod.GET, business + "/listings", null),
                new Endpoint(HttpMethod.POST, business + "/listings", """
                        {"title": "Paket", "description": "Açıklama", "allergenInfo": "Yok.", "normalPrice": "10.00",
                         "salePrice": "5.00", "totalQuantity": 1, "pickupStart": "2031-03-10T19:00:00",
                         "pickupEnd": "2031-03-10T20:00:00"}
                        """),
                new Endpoint(
                        HttpMethod.PUT, business + "/listings/" + listing + "/price", "{\"salePrice\": \"50.00\"}"),
                new Endpoint(HttpMethod.POST, business + "/listings/" + listing + "/deactivate", null),
                new Endpoint(HttpMethod.GET, business + "/orders", null),
                new Endpoint(HttpMethod.POST, business + "/pickups/verify", "{\"code\": \"123456\"}"),
                new Endpoint(HttpMethod.GET, business + "/reports?from=2031-03-01&to=2031-03-31", null));
    }

    private List<Endpoint> adminEndpoints() {
        return List.of(
                new Endpoint(HttpMethod.GET, "/api/v1/admin/business-applications", null),
                new Endpoint(HttpMethod.POST, "/api/v1/admin/businesses/" + bakery + "/approve", null),
                new Endpoint(HttpMethod.POST, "/api/v1/admin/businesses/" + bakery + "/reject", null),
                new Endpoint(HttpMethod.GET, "/api/v1/admin/users", null),
                new Endpoint(HttpMethod.PUT, "/api/v1/admin/users/" + customer.id() + "/active", "{\"active\": false}"),
                new Endpoint(HttpMethod.PUT, "/api/v1/admin/businesses/" + bakery + "/active", "{\"active\": false}"),
                new Endpoint(HttpMethod.PUT, "/api/v1/admin/users/" + customer.id() + "/roles/YONETICI", null),
                new Endpoint(HttpMethod.DELETE, "/api/v1/admin/users/" + customer.id() + "/roles/MUSTERI", null),
                new Endpoint(HttpMethod.GET, "/api/v1/admin/listings", null),
                new Endpoint(HttpMethod.POST, "/api/v1/admin/listings/" + listing + "/deactivate", null),
                new Endpoint(HttpMethod.GET, "/api/v1/admin/reports/platform?from=2031-03-01&to=2031-03-31", null));
    }

    private static void expect(List<String> failures, ApiClient client, Endpoint endpoint, int status, String code)
            throws Exception {
        MockHttpServletRequestBuilder builder = request(endpoint.method(), endpoint.path());
        if (endpoint.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
        }
        MockHttpServletResponse response = client.perform(builder).andReturn().getResponse();
        String body = response.getContentAsString();
        if (response.getStatus() != status || !body.contains("\"code\":\"" + code + "\"")) {
            failures.add(endpoint.method() + " " + endpoint.path() + " -> " + response.getStatus() + " " + body);
        }
    }

    /** An endpoint call: method, path with query and an optional JSON body. */
    private record Endpoint(HttpMethod method, String path, String body) {}
}
