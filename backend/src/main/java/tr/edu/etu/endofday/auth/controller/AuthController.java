package tr.edu.etu.endofday.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tr.edu.etu.endofday.auth.AuthenticatedUser;
import tr.edu.etu.endofday.auth.service.LoginService;
import tr.edu.etu.endofday.auth.service.RegistrationService;
import tr.edu.etu.endofday.user.controller.UserResponse;
import tr.edu.etu.endofday.user.service.UserService;

/**
 * Registration, sign-in and the CSRF token. Sign-out is handled by Spring Security's logout filter (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final UserService userService;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public AuthController(
            RegistrationService registrationService,
            LoginService loginService,
            UserService userService,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.userService = userService;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Spring Security creates the CSRF token lazily and writes the XSRF-TOKEN cookie only when the token is read, so
     * this endpoint reads it.
     */
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(registrationService.register(
                request.firstName(), request.lastName(), request.email(), request.phone(), request.password()));
    }

    /**
     * Signs the user in: changes the id of an existing session (otherwise a new session is created when the user id is
     * stored), replaces the CSRF token and stores the user id in the session.
     */
    @PostMapping("/login")
    public UserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthenticatedUser user = loginService.authenticate(request.email(), request.password());
        Authentication authentication = user.toAuthentication();

        // Session fixation protection and CSRF token replacement.
        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        // The replacement token is lazy as well; reading it writes the new XSRF-TOKEN cookie into this response.
        if (httpRequest.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken newToken) {
            newToken.getToken();
        }
        return UserResponse.from(userService.getProfile(user.id()));
    }
}
