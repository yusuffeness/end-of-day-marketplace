package tr.edu.etu.endofday.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import tr.edu.etu.endofday.auth.AuthenticatedUser;
import tr.edu.etu.endofday.common.error.ApiException;
import tr.edu.etu.endofday.user.EmailAddresses;
import tr.edu.etu.endofday.user.repository.UserAccess;
import tr.edu.etu.endofday.user.repository.UserRepository;

/** Checks email address and password and returns the user to store in the session. */
@Service
public class LoginService {

    static final String BAD_CREDENTIALS_CODE = "EOD-010";
    static final String BAD_CREDENTIALS_DETAIL = "E-posta veya parola hatalı.";
    static final String INACTIVE_ACCOUNT_CODE = "EOD-011";
    static final String INACTIVE_ACCOUNT_DETAIL = "Hesabınız pasif durumda.";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;

    public LoginService(AuthenticationManager authenticationManager, UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
    }

    /**
     * Authenticates the user. An unknown email address and a wrong password give the same error, so the response does
     * not reveal whether an account exists. The active flag is checked only after the password matched.
     *
     * @throws ApiException {@code EOD-010} for wrong credentials, {@code EOD-011} for an inactive account
     */
    public AuthenticatedUser authenticate(String email, String password) {
        long userId;
        try {
            UserDetails details = (UserDetails) authenticationManager
                    .authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                            EmailAddresses.normalize(email), password))
                    .getPrincipal();
            userId = Long.parseLong(details.getUsername());
        } catch (BadCredentialsException ex) {
            throw badCredentials();
        } catch (DisabledException ex) {
            throw new ApiException(HttpStatus.FORBIDDEN, INACTIVE_ACCOUNT_CODE, INACTIVE_ACCOUNT_DETAIL);
        }
        // Read the roles exactly as every later request of the session will.
        UserAccess access = userRepository.findAccess(userId).orElseThrow(LoginService::badCredentials);
        return new AuthenticatedUser(access.id(), access.roles());
    }

    private static ApiException badCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS_CODE, BAD_CREDENTIALS_DETAIL);
    }
}
