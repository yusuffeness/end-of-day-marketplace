package tr.edu.etu.endofday.common.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;
import tr.edu.etu.endofday.common.error.ApiException;

/**
 * Answers unauthenticated (401) and forbidden (403) requests with the same problem detail format as the rest of the
 * API by passing an {@link ApiException} to the MVC exception resolvers.
 */
class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String UNAUTHORIZED_CODE = "EOD-401";
    static final String UNAUTHORIZED_DETAIL = "Bu işlem için giriş yapmalısınız.";
    static final String FORBIDDEN_CODE = "EOD-403";
    static final String FORBIDDEN_DETAIL = "Bu işlem için yetkiniz yok.";

    private final HandlerExceptionResolver exceptionResolver;

    ProblemDetailSecurityHandler(HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
        resolve(request, response, new ApiException(HttpStatus.UNAUTHORIZED, UNAUTHORIZED_CODE, UNAUTHORIZED_DETAIL));
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) {
        resolve(request, response, new ApiException(HttpStatus.FORBIDDEN, FORBIDDEN_CODE, FORBIDDEN_DETAIL));
    }

    private void resolve(HttpServletRequest request, HttpServletResponse response, ApiException exception) {
        exceptionResolver.resolveException(request, response, null, exception);
    }
}
