package tr.edu.etu.endofday.common.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions into RFC 9457 problem details that carry an additional {@code code} property and a Turkish
 * {@code detail}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String CODE_PROPERTY = "code";

    static final String BAD_REQUEST_CODE = "EOD-400";
    static final String BAD_REQUEST_DETAIL = "Gönderilen bilgiler geçersiz.";
    static final String NOT_FOUND_CODE = "EOD-404";
    static final String NOT_FOUND_DETAIL = "İstenen kaynak bulunamadı.";
    static final String INTERNAL_ERROR_CODE = "EOD-500";
    static final String INTERNAL_ERROR_DETAIL = "Beklenmeyen bir hata oluştu.";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        return problem(ex.getStatus(), ex.getCode(), ex.getDetail());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_CODE, BAD_REQUEST_DETAIL);
    }

    /** Lets Spring Security answer with its own entry point and access denied handler. */
    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    public void rethrowSecurityException(RuntimeException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unexpected error while processing the request", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_CODE, INTERNAL_ERROR_DETAIL);
    }

    /**
     * Adds the error code and a Turkish detail to the problem details that Spring MVC creates for its own exceptions,
     * such as validation failures, unreadable request bodies and unknown paths.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problemDetail) {
            if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
                problemDetail.setDetail(BAD_REQUEST_DETAIL);
                problemDetail.setProperty(CODE_PROPERTY, BAD_REQUEST_CODE);
            } else if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
                problemDetail.setDetail(NOT_FOUND_DETAIL);
                problemDetail.setProperty(CODE_PROPERTY, NOT_FOUND_CODE);
            } else if (statusCode.is5xxServerError()) {
                log.error("Request failed with status {}", statusCode.value(), ex);
                problemDetail.setDetail(INTERNAL_ERROR_DETAIL);
                problemDetail.setProperty(CODE_PROPERTY, INTERNAL_ERROR_CODE);
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatusCode status, String code, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty(CODE_PROPERTY, code);
        return ResponseEntity.status(status).body(problemDetail);
    }
}
