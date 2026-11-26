package tr.edu.etu.endofday.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Drives MockMvc the way the browser client uses the API: it keeps the cookies the server sets, sends the XSRF-TOKEN
 * cookie back in the X-XSRF-TOKEN header and keeps the HTTP session between requests.
 *
 * <p>No test CSRF shortcut is involved, so the real token repository of the application is exercised.
 */
public final class ApiClient {

    public static final String CSRF_COOKIE = "XSRF-TOKEN";
    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private final MockMvc mockMvc;
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private MockHttpSession session;

    public ApiClient(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /** Sends the request with the stored cookies, CSRF header and session, then stores what the response set. */
    public ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        if (session != null) {
            // A browser sends the session id in the JSESSIONID cookie; mark it as requested by the client, as the
            // servlet container does, so that the session counts as one the client brought along.
            MockHttpSession current = session;
            request.session(current).with(servletRequest -> {
                servletRequest.setRequestedSessionId(current.getId());
                servletRequest.setRequestedSessionIdValid(true);
                servletRequest.setRequestedSessionIdFromCookie(true);
                return servletRequest;
            });
        }
        String csrfToken = cookies.get(CSRF_COOKIE);
        if (csrfToken != null) {
            request.header(CSRF_HEADER, csrfToken);
        }
        if (!cookies.isEmpty()) {
            request.cookie(cookies.entrySet().stream()
                    .map(cookie -> new Cookie(cookie.getKey(), cookie.getValue()))
                    .toArray(Cookie[]::new));
        }
        ResultActions actions = mockMvc.perform(request);
        MvcResult result = actions.andReturn();
        // Applied in order, as a browser does: logging in first clears the old token, then sets the new one.
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), cookie.getValue());
            }
        }
        HttpSession current = result.getRequest().getSession(false);
        session = current instanceof MockHttpSession mockSession && !mockSession.isInvalid() ? mockSession : null;
        return actions;
    }

    public ResultActions fetchCsrfToken() throws Exception {
        return perform(get("/api/v1/auth/csrf")).andExpect(status().isNoContent());
    }

    public ResultActions postJson(String path, String json) throws Exception {
        return perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public ResultActions register(String firstName, String lastName, String email, String password) throws Exception {
        return postJson("/api/v1/auth/register", """
                {"firstName": "%s", "lastName": "%s", "email": "%s", "password": "%s"}
                """.formatted(firstName, lastName, email, password));
    }

    public ResultActions login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password));
    }

    public ResultActions logout() throws Exception {
        return perform(post("/api/v1/auth/logout"));
    }

    /** The current XSRF-TOKEN cookie value, or {@code null}. */
    public String csrfToken() {
        return cookies.get(CSRF_COOKIE);
    }

    /** The current, valid session, or {@code null}. */
    public MockHttpSession session() {
        return session;
    }

    /** Starts the next request in this session, for example an anonymous session that existed before login. */
    public void useSession(MockHttpSession session) {
        this.session = session;
    }

    /**
     * A second client with the same cookies and session, like another browser tab. A client is not thread-safe, so
     * concurrent requests of one user each use their own copy.
     */
    public ApiClient copy() {
        ApiClient copy = new ApiClient(mockMvc);
        copy.cookies.putAll(cookies);
        copy.session = session;
        return copy;
    }
}
